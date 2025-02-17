/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery;

import org.exist.dom.QName;
import org.exist.dom.persistent.NodeSet;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.*;

import java.util.HashSet;
import java.util.Set;

/**
 * Represents an XQuery "for" expression.
 * 
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public class ForExpr extends BindingExpression {

    private QName positionalVariable = null;
    private boolean allowEmpty = false;
    private boolean isOuterFor = true;

    public ForExpr(XQueryContext context, boolean allowingEmpty) {
        super(context);
        this.allowEmpty = allowingEmpty;
    }

    @Override
    public ClauseType getType() {
        return ClauseType.FOR;
    }

    /**
     * A "for" expression may have an optional positional variable whose
     * QName can be set via this method.
     * 
     * @param variable the name of the variable to set
     */
    public void setPositionalVariable(final QName variable) {
        positionalVariable = variable;
    }

	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#analyze(org.exist.xquery.Expression)
     */
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        super.analyze(contextInfo);
        // Save the local variable stack
        final LocalVariable mark = context.markLocalVariables(false);
        try {
            contextInfo.setParent(this);
            final AnalyzeContextInfo varContextInfo = new AnalyzeContextInfo(contextInfo);
            inputSequence.analyze(varContextInfo);
            // Declare the iteration variable
            final LocalVariable inVar = new LocalVariable(varName);
            inVar.setSequenceType(sequenceType);
            inVar.setStaticType(varContextInfo.getStaticReturnType());
            context.declareVariableBinding(inVar);
            // Declare positional variable
            if (positionalVariable != null) {
                final LocalVariable posVar = new LocalVariable(positionalVariable);
                posVar.setSequenceType(POSITIONAL_VAR_TYPE);
                posVar.setStaticType(Type.INTEGER);
                context.declareVariableBinding(posVar);
            }

            final AnalyzeContextInfo newContextInfo = new AnalyzeContextInfo(contextInfo);
            newContextInfo.addFlag(SINGLE_STEP_EXECUTION);
            returnExpr.analyze(newContextInfo);
        } finally {
            // restore the local variable stack
            context.popLocalVariables(mark);
        }
    }

    /**
     * This implementation tries to process the "where" clause in advance, i.e. in one single
     * step. This is possible if the input sequence is a node set and the where expression
     * has no dependencies on other variables than those declared in this "for" statement.
     * 
     * @see org.exist.xquery.Expression#eval(Sequence, Item)
     */
    public Sequence eval(Sequence contextSequence, Item contextItem)
            throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES,
                "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES,
                "CONTEXT SEQUENCE", contextSequence);}
            if (contextItem != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES,
                "CONTEXT ITEM", contextItem.toSequence());}
        }
        context.expressionStart(this);
        LocalVariable var;
        Sequence in;
        // Save the local variable stack
        LocalVariable mark = context.markLocalVariables(false);
        Sequence resultSequence = new ValueSequence(unordered);
        try {
            // Evaluate the "in" expression
            in = inputSequence.eval(contextSequence, null);
            clearContext(getExpressionId(), in);
            // Declare the iteration variable
            var = createVariable(varName);
            var.setSequenceType(sequenceType);
            context.declareVariableBinding(var);
            registerUpdateListener(in);
            // Declare positional variable
            LocalVariable at = null;
            if (positionalVariable != null) {
                at = new LocalVariable(positionalVariable);
                at.setSequenceType(POSITIONAL_VAR_TYPE);
                context.declareVariableBinding(at);
            }
            // Assign the whole input sequence to the bound variable.
            // This is required if we process the "where" or "order by" clause
            // in one step.
            var.setValue(in);
            // Save the current context document set to the variable as a hint
            // for path expressions occurring in the "return" clause.
            if (in instanceof NodeSet) {
                var.setContextDocs(in.getDocumentSet());
            } else {
                var.setContextDocs(null);
            }
            // See if we can process the "where" clause in a single step (instead of
            // calling the where expression for each item in the input sequence)
            // This is possible if the input sequence is a node set and has no
            // dependencies on the current context item.
            if (isOuterFor) {
                if (returnExpr instanceof WhereClause) {
                    if (at == null) {
                        in = ((WhereClause) returnExpr).preEval(in);
                    }
                } else if (returnExpr instanceof FLWORClause) {
                    in = ((FLWORClause) returnExpr).preEval(in);
                }
            }

            final IntegerValue atVal = new IntegerValue(this, 1);
            if (positionalVariable != null) {
                at.setValue(atVal);
            }
            //Type.EMPTY is *not* a subtype of other types ;
            //the tests below would fail without this prior cardinality check
            if (in.isEmpty() && sequenceType != null &&
                    !sequenceType.getCardinality().isSuperCardinalityOrEqualOf(Cardinality.EMPTY_SEQUENCE)) {
                throw new XPathException(this, ErrorCodes.XPTY0004,
                        "Invalid cardinality for variable $" + varName +
                                ". Expected " + sequenceType.getCardinality().getHumanDescription() +
                                ", got " + in.getCardinality().getHumanDescription());
            }

            // Loop through each variable binding
            int p = 0;
            if (in.isEmpty() && allowEmpty) {
                processItem(var, AtomicValue.EMPTY_VALUE, Sequence.EMPTY_SEQUENCE, resultSequence, at, p);
            } else {
                for (final SequenceIterator i = in.iterate(); i.hasNext(); p++) {
                    processItem(var, i.nextItem(), in, resultSequence, at, p);
                }
            }
        } finally {
            // restore the local variable stack 
            context.popLocalVariables(mark, resultSequence);
        }

        clearContext(getExpressionId(), in);
        if (sequenceType != null) {
            //Type.EMPTY is *not* a subtype of other types ; checking cardinality first
            //only a check on empty sequence is accurate here
            if (resultSequence.isEmpty() &&
                    !sequenceType.getCardinality().isSuperCardinalityOrEqualOf(Cardinality.EMPTY_SEQUENCE))
                {throw new XPathException(this, ErrorCodes.XPTY0004,
                    "Invalid cardinality for variable $" + varName + ". Expected " +
                    sequenceType.getCardinality().getHumanDescription() +
                    ", got " + Cardinality.EMPTY_SEQUENCE.getHumanDescription());}
            //TODO : ignore nodes right now ; they are returned as xs:untypedAtomicType
            if (!Type.subTypeOf(sequenceType.getPrimaryType(), Type.NODE)) {
                if (!resultSequence.isEmpty() &&
                        !Type.subTypeOf(resultSequence.getItemType(),
                        sequenceType.getPrimaryType()))
                    {throw new XPathException(this, ErrorCodes.XPTY0004,
                        "Invalid type for variable $" + varName +
                        ". Expected " + Type.getTypeName(sequenceType.getPrimaryType()) +
                        ", got " +Type.getTypeName(resultSequence.getItemType()));}
            //trigger the old behaviour
            } else {
                var.checkType();
            }
        }
        setActualReturnType(resultSequence.getItemType());

        if (callPostEval()) {
            resultSequence = postEval(resultSequence);
        }

        context.expressionEnd(this);
        if (context.getProfiler().isEnabled())
            {context.getProfiler().end(this, "", resultSequence);}
        return resultSequence;
    }

    private void processItem(LocalVariable var, Item contextItem, Sequence in, Sequence resultSequence, LocalVariable
            at, int p) throws XPathException {
        context.proceed(this);
        context.setContextSequencePosition(p, in);
        if (positionalVariable != null) {
            final int position = contextItem == AtomicValue.EMPTY_VALUE ? 0 : p + 1;
            at.setValue(new IntegerValue(this, position));
        }
        final Sequence contextSequence = contextItem.toSequence();
        // set variable value to current item
        var.setValue(contextSequence);
        if (sequenceType == null)
            {var.checkType();} //because it makes some conversions !
        //Reset the context position
        context.setContextSequencePosition(0, null);

        final Sequence returnExprResult;
        if (returnExpr instanceof OrderByClause) {
            returnExprResult = returnExpr.eval(contextSequence, null);
        } else {
            returnExprResult = returnExpr.eval(null, null);
        }
        resultSequence.addAll(returnExprResult);

        // free resources
        var.destroy(context, resultSequence);
    }

    private boolean callPostEval() {
        FLWORClause prev = getPreviousClause();
        while (prev != null) {
            switch (prev.getType()) {
                case LET:
                case FOR:
                    return false;
                case ORDERBY:
                case GROUPBY:
                    return true;
            }
            prev = prev.getPreviousClause();
        }
        return true;
    }

    @Override
    public Sequence preEval(Sequence seq) throws XPathException {
        // if preEval gets called, we know we're inside another FOR
        isOuterFor = false;
        return super.preEval(seq);
    }

    @Override
    public void dump(ExpressionDumper dumper) {
        dumper.display("for ", line);
        dumper.startIndent();
        dumper.display("$").display(varName);
        if (sequenceType != null) {
            dumper.display(" as ").display(sequenceType);
        }
        if (allowEmpty) {
            dumper.display(" allowing empty ");
        }
        if (positionalVariable != null)
            {dumper.display(" at ").display(positionalVariable);}
        dumper.display(" in ");
        inputSequence.dump(dumper);
        dumper.endIndent().nl();
        //TODO : QuantifiedExpr
        if (returnExpr instanceof LetExpr)
            {dumper.display(" ", returnExpr.getLine());}
        else
            {dumper.display("return", returnExpr.getLine());} 
        dumper.startIndent();
        returnExpr.dump(dumper);
        dumper.endIndent().nl();
    }

    @Override
    public String toString() {
        final StringBuilder result = new StringBuilder();
        result.append("for ");
        result.append("$").append(varName);
        if (sequenceType != null)
            {result.append(" as ").append(sequenceType);}
        if (allowEmpty) {
            result.append(" allowing empty ");
        }
        if (positionalVariable != null) {
            result.append(" at ").append(positionalVariable);
        }
        result.append(" in ");
        result.append(inputSequence.toString());
        result.append(" ");
        //TODO : QuantifiedExpr
        if (returnExpr instanceof LetExpr)
            {result.append(" ");}
        else
            {result.append("return ");}
        result.append(returnExpr.toString());
        return result.toString();
    }

    @Override
    public void accept(final ExpressionVisitor visitor) {
        visitor.visitForExpression(this);
    }

    @Override
    public Set<QName> getTupleStreamVariables() {
        final Set<QName> variables = new HashSet<>();
        if (positionalVariable != null) {
            variables.add(positionalVariable);
        }

        final QName variable = getVariable();
        if (variable != null) {
            variables.add(variable);
        }

        final LocalVariable startVar = getStartVariable();
        if (startVar != null) {
            variables.add(startVar.getQName());
        }

        return variables;
    }
}
