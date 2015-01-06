package com.tinkerpop.gremlin.process.graph.step.branch;

import com.tinkerpop.gremlin.process.Step;
import com.tinkerpop.gremlin.process.Traversal;
import com.tinkerpop.gremlin.process.Traverser;
import com.tinkerpop.gremlin.process.graph.step.util.MarkerIdentityStep;
import com.tinkerpop.gremlin.process.util.AbstractStep;
import com.tinkerpop.gremlin.process.util.TraversalHelper;

import java.util.NoSuchElementException;
import java.util.function.Predicate;

/**
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class RepeatStep<S> extends AbstractStep<S, S> {

    private Traversal<S, S> repeatTraversal = null;
    private Predicate<Traverser<S>> untilPredicate = null;
    private Predicate<Traverser<S>> emitPredicate = null;
    private boolean untilFirst = false;
    private boolean emitFirst = false;
    private Step<?, S> endStep = null;

    public RepeatStep(final Traversal traversal) {
        super(traversal);
    }

    public void setRepeatTraversal(final Traversal<S, S> repeatTraversal) {
        this.repeatTraversal = repeatTraversal;
        this.repeatTraversal.asAdmin().addStep(new MarkerIdentityStep<>(this.repeatTraversal)); // TODO: this is really bad
        this.repeatTraversal.asAdmin().mergeSideEffects(this.getTraversal().asAdmin().getSideEffects());
    }

    public void setUntilPredicate(final Predicate<Traverser<S>> untilPredicate) {
        if (null == this.repeatTraversal) this.untilFirst = true;
        this.untilPredicate = untilPredicate;
    }

    public void setEmitPredicate(final Predicate<Traverser<S>> emitPredicate) {
        if (null == this.repeatTraversal) this.emitFirst = true;
        this.emitPredicate = emitPredicate;
    }

    public Traversal<S, S> getRepeatTraversal() {
        return this.repeatTraversal;
    }

    public Predicate<Traverser<S>> getUntilPredicate() {
        return this.untilPredicate;
    }

    public Predicate<Traverser<S>> getEmitPredicate() {
        return this.emitPredicate;
    }

    public boolean isUntilFirst() {
        return this.untilFirst;
    }

    public boolean isEmitFirst() {
        return this.emitFirst;
    }

    private final boolean doUntil(final Traverser<S> traverser) {
        return null == this.untilPredicate || this.untilPredicate.test(traverser);
    }

    private final boolean doEmit(final Traverser<S> traverser) {
        return null != this.emitPredicate && this.emitPredicate.test(traverser);
    }

    ///

    public static class LoopPredicate<S> implements Predicate<Traverser<S>> {
        private final int maxLoops;

        public LoopPredicate(final int maxLoops) {
            this.maxLoops = maxLoops;
        }

        @Override
        public boolean test(final Traverser<S> traverser) {
            return traverser.loops() >= this.maxLoops;
        }
    }

    protected Traverser<S> processNextStart() throws NoSuchElementException {
        if (null == this.endStep) this.endStep = TraversalHelper.getEnd(this.repeatTraversal);
        ////
        while (true) {
            if (this.repeatTraversal.hasNext()) {
                final Traverser.Admin<S> s = this.endStep.next().asAdmin();
                s.incrLoops();
                if (doUntil(s)) {
                    s.resetLoops();
                    return s;
                } else {
                    this.repeatTraversal.asAdmin().addStart(s);
                    if (doEmit(s)) {
                        final Traverser.Admin<S> emitSplit = s.split();
                        emitSplit.resetLoops();
                        return emitSplit;
                    }
                }
            } else {
                final Traverser.Admin<S> s = this.starts.next();
                if (this.untilFirst && doUntil(s)) {
                    s.resetLoops();
                    return s;
                }
                this.repeatTraversal.asAdmin().addStart(s);
                if (this.emitFirst && doEmit(s)) {
                    final Traverser.Admin<S> emitSplit = s.split();
                    emitSplit.resetLoops();
                    return emitSplit;
                }
            }
        }
    }

    @Override
    public String toString() {
        return TraversalHelper.makeStepString(this, this.repeatTraversal);
    }
}
