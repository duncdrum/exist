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

import org.exist.Namespaces;
import org.exist.dom.QName;
import org.easymock.EasyMock;
import org.exist.xquery.value.Sequence;
import org.junit.Test;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

/**
 * Tests for {@link Profiler}, covering the start/end stack management,
 * verbosity-based logging branches, and mismatched end() error handling.
 *
 * @author eXist-db contributors
 */
public class ProfilerTest {

    private Option profileOption(final String value) throws XPathException {
        return new Option(new QName("profiling", Namespaces.EXIST_NS, "exist"), value);
    }

     /**
      * start/end pairing with enabled profiler and verbosity 0
      * exercises the base path: push, pop, compute elapsed, log END.
      */
     @Test
    public void startEnd_pair_valid_popStack() throws Exception {
        final Profiler profiler = new Profiler(null);
        profiler.setEnabled(true);
        profiler.setVerbosity(0);

        final Expression mockExpr = createMock(Expression.class);
        expect(mockExpr.getLine()).andReturn(10).anyTimes();
        expect(mockExpr.getColumn()).andReturn(5).anyTimes();
        replay(mockExpr);

        profiler.start(mockExpr);
        profiler.end(mockExpr, "test message", Sequence.EMPTY_SEQUENCE);

        verify(mockExpr);
     }

     /**
      * Nested start/end verifies LIFO ordering: innerExpr must end before outerExpr,
      * otherwise end(inner) would try to pop outerExpr from the stack.
      */
     @Test
    public void startEnd_nestedLIFO_order() throws Exception {
        final Profiler profiler = new Profiler(null);
        profiler.setEnabled(true);
        profiler.setVerbosity(0);

        final Expression outerExpr = createMock(Expression.class);
        expect(outerExpr.getLine()).andReturn(1).anyTimes();
        expect(outerExpr.getColumn()).andReturn(0).anyTimes();
        replay(outerExpr);

        final Expression innerExpr = createMock(Expression.class);
        expect(innerExpr.getLine()).andReturn(2).anyTimes();
        expect(innerExpr.getColumn()).andReturn(0).anyTimes();
        replay(innerExpr);

        profiler.start(outerExpr);
        profiler.start(innerExpr);
        profiler.end(innerExpr, "inner done", Sequence.EMPTY_SEQUENCE);
        profiler.end(outerExpr, "outer done", Sequence.EMPTY_SEQUENCE);

        verify(outerExpr, innerExpr);
     }

     /**
      * end() with a non-matching expression (mismatched end) triggers
      * the warning branch and stack.clear() inside the try block.
      */
     @Test
    public void startEnd_mismatchedExpression_logsWarning() throws Exception {
        final Profiler profiler = new Profiler(null);
        profiler.setEnabled(true);
        profiler.setVerbosity(0);

        final Expression expr1 = createMock(Expression.class);
        expect(expr1.getLine()).andReturn(1).anyTimes();
        expect(expr1.getColumn()).andReturn(0).anyTimes();
        replay(expr1);

        final Expression expr2 = createMock(Expression.class);
        expect(expr2.getLine()).andReturn(2).anyTimes();
        expect(expr2.getColumn()).andReturn(0).anyTimes();
        replay(expr2);

        profiler.start(expr1);
        profiler.end(expr2, "mismatched end", Sequence.EMPTY_SEQUENCE);

        verify(expr1, expr2);
     }

     /**
      * end() called without a matching start() should be caught by
      * the RuntimeException catch block (stack.pop() throws EmptyStackException).
      */
     @Test
    public void end_withoutStart_isCaught() throws Exception {
        final Profiler profiler = new Profiler(null);
        profiler.setEnabled(true);
        profiler.setVerbosity(0);

        final Expression mockExpr = createMock(Expression.class);
        replay(mockExpr);

        profiler.end(mockExpr, "unpaired end", Sequence.EMPTY_SEQUENCE);

        verify(mockExpr);
     }

     /**
      * When profiling is disabled, start() and end() return early without
      * touching the stack or calling any Expression methods.
      */
     @Test
    public void disabledProfiler_doesNothing() throws Exception {
        final Profiler profiler = new Profiler(null);
        profiler.setEnabled(false); // default is false
        profiler.setVerbosity(10);

         // No expectations set -- start/end should return early, never calling mockExpr methods
        final Expression mockExpr = createMock(Expression.class);
        replay(mockExpr);

        profiler.start(mockExpr);
        profiler.end(mockExpr, "should be ignored", Sequence.EMPTY_SEQUENCE);

        verify(mockExpr);
     }

     /**
      * Verbosity ITEM_COUNT (5): result.getItemCount() path is exercised.
      */
     @Test
    public void verbosity_itemCount_logsItemCount() throws Exception {
        final Profiler profiler = new Profiler(null);
        profiler.setEnabled(true);
        profiler.setVerbosity(Profiler.ITEM_COUNT);

        final Expression mockExpr = createMock(Expression.class);
        expect(mockExpr.getLine()).andReturn(1).anyTimes();
        expect(mockExpr.getColumn()).andReturn(0).anyTimes();
        replay(mockExpr);

        final Sequence mockSeq = createMock(Sequence.class);
        expect(mockSeq.getItemCount()).andReturn(42).anyTimes();
        replay(mockSeq);

        profiler.start(mockExpr);
        profiler.end(mockExpr, "item count test", mockSeq);

        verify(mockExpr, mockSeq);
      }

      /**
       * Verbosity TIME (1): elapsed time computation branch is exercised.
       */
      @Test
    public void verbosity_time_logsElapsedTime() throws Exception {
        final Profiler profiler = new Profiler(null);
        profiler.setEnabled(true);
        profiler.setVerbosity(Profiler.TIME);

        final Expression mockExpr = createMock(Expression.class);
        expect(mockExpr.getLine()).andReturn(1).anyTimes();
        expect(mockExpr.getColumn()).andReturn(0).anyTimes();
        replay(mockExpr);

        profiler.start(mockExpr);
        Thread.sleep(50);
        profiler.end(mockExpr, "time test", null);

        verify(mockExpr);
      }

     /**
      * Null message is handled without NPE.
      */
     @Test
    public void end_nullMessage_doesNotThrow() throws Exception {
        final Profiler profiler = new Profiler(null);
        profiler.setEnabled(true);
        profiler.setVerbosity(0);

        final Expression mockExpr = createMock(Expression.class);
        expect(mockExpr.getLine()).andReturn(1).anyTimes();
        expect(mockExpr.getColumn()).andReturn(0).anyTimes();
        replay(mockExpr);

        profiler.start(mockExpr);
        profiler.end(mockExpr, null, Sequence.EMPTY_SEQUENCE);

        verify(mockExpr);
     }

     /**
      * Empty string message should also not throw.
      */
     @Test
    public void end_emptyMessage_doesNotThrow() throws Exception {
        final Profiler profiler = new Profiler(null);
        profiler.setEnabled(true);
        profiler.setVerbosity(0);

        final Expression mockExpr = createMock(Expression.class);
        expect(mockExpr.getLine()).andReturn(1).anyTimes();
        expect(mockExpr.getColumn()).andReturn(0).anyTimes();
        replay(mockExpr);

        profiler.start(mockExpr);
        profiler.end(mockExpr, "", Sequence.EMPTY_SEQUENCE);

        verify(mockExpr);
     }

     /**
      * start() followed by null message with verbosity ITEM_COUNT and result.
      */
     @Test
    public void startEnd_NULLmessage_verbosityItemCount() throws Exception {
        final Profiler profiler = new Profiler(null);
        profiler.setEnabled(true);
        profiler.setVerbosity(Profiler.ITEM_COUNT);

        final Expression mockExpr = createMock(Expression.class);
        expect(mockExpr.getLine()).andReturn(1).anyTimes();
        expect(mockExpr.getColumn()).andReturn(0).anyTimes();
        replay(mockExpr);

        final Sequence mockSeq = createMock(Sequence.class);
        expect(mockSeq.getItemCount()).andReturn(7).anyTimes();
        replay(mockSeq);

        profiler.start(mockExpr);
        profiler.end(mockExpr, null, mockSeq);

        verify(mockExpr, mockSeq);
     }

     /**
      * Multiple start/end pairs in sequence (not nested) -- each pair should
      * independently push and pop from the stack.
      */
     @Test
    public void startEnd_multiplePairs_inSequence() throws Exception {
        final Profiler profiler = new Profiler(null);
        profiler.setEnabled(true);
        profiler.setVerbosity(0);

        for (int i = 0; i < 3; i++) {
            final Expression mockExpr = createMock(Expression.class);
            expect(mockExpr.getLine()).andReturn(i).anyTimes();
            expect(mockExpr.getColumn()).andReturn(0).anyTimes();
            replay(mockExpr);

            profiler.start(mockExpr);
            profiler.end(mockExpr, "pair " + i, Sequence.EMPTY_SEQUENCE);

            verify(mockExpr);
         }
     }

     /**
      * Triple-nested start/end: outer wraps middle wraps inner.
      */
     @Test
    public void startEnd_tripleNested() throws Exception {
        final Profiler profiler = new Profiler(null);
        profiler.setEnabled(true);
        profiler.setVerbosity(0);

        final Expression outer = createMock(Expression.class);
        expect(outer.getLine()).andReturn(1).anyTimes();
        expect(outer.getColumn()).andReturn(0).anyTimes();
        replay(outer);

        final Expression middle = createMock(Expression.class);
        expect(middle.getLine()).andReturn(5).anyTimes();
        expect(middle.getColumn()).andReturn(0).anyTimes();
        replay(middle);

        final Expression inner = createMock(Expression.class);
        expect(inner.getLine()).andReturn(10).anyTimes();
        expect(inner.getColumn()).andReturn(0).anyTimes();
        replay(inner);

        profiler.start(outer);
        profiler.start(middle);
        profiler.start(inner);
        profiler.end(inner, "inner", Sequence.EMPTY_SEQUENCE);
        profiler.end(middle, "middle", Sequence.EMPTY_SEQUENCE);
        profiler.end(outer, "outer", Sequence.EMPTY_SEQUENCE);

        verify(outer, middle, inner);
     }

     /**
      * Test reset() with stack not empty logs QUERY RESET.
      */
     @Test
    public void reset_withNonEmptyStack_logsReset() throws Exception {
        final Profiler profiler = new Profiler(null);
        profiler.setEnabled(true);
        profiler.setVerbosity(0);

        final Expression mockExpr = createMock(Expression.class);
        expect(mockExpr.getLine()).andReturn(1).anyTimes();
        expect(mockExpr.getColumn()).andReturn(0).anyTimes();
        replay(mockExpr);

        profiler.start(mockExpr);
        profiler.reset();

        verify(mockExpr);
     }

     /**
      * Test that traceFunctions() returns true when stats are enabled.
      */
     @Test
    public void traceFunctions_statsEnabled_returnsTrue() throws XPathException {
        final Profiler profiler = new Profiler(null);
        profiler.configure(profileOption("trace=yes"));
        assertTrue(profiler.traceFunctions());
     }

     /**
      * Test that configure() handles invalid verbosity gracefully.
      */
     @Test
    public void configure_invalidVerbosity_staysAtZero() throws XPathException {
        final Profiler profiler = new Profiler(null);
        profiler.configure(profileOption("verbosity=notanumber"));
         // Invalid verbosity falls through to default (0), which disables profiling
        assertFalse(profiler.isEnabled());
     }

      /**
       * Test that configure() with valid verbosity sets verbosity level.
       */
      @Test
    public void configure_validVerbosity_setsVerbosity() throws XPathException {
        final Profiler profiler = new Profiler(null);
        profiler.configure(profileOption("verbosity=1"));
        assertEquals(1, profiler.verbosity());
      }

     /**
      * Test logger name configuration via options.
      */
     @Test
    public void configure_customLoggerName() throws XPathException {
        final Profiler profiler = new Profiler(null);
        profiler.configure(profileOption("logger=test.logger.name"));
         // The logger is set internally, verify the profiler still works
        profiler.setEnabled(true);
        profiler.setVerbosity(0);

        final Expression mockExpr = createMock(Expression.class);
        expect(mockExpr.getLine()).andReturn(1).anyTimes();
        expect(mockExpr.getColumn()).andReturn(0).anyTimes();
        replay(mockExpr);

        profiler.start(mockExpr);
        profiler.end(mockExpr, "logger test", Sequence.EMPTY_SEQUENCE);

        verify(mockExpr);
     }
}
