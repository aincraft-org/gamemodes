package dev.jlo.gamemodes.domain.common;

import java.util.Comparator;
import java.util.Objects;

public sealed interface MatchEvent permits MatchEvent.Objective, MatchEvent.ScoreUpdate,
        MatchEvent.VictoryCheck, MatchEvent.DeadlineCheck {
    long getTick();

    int getPriority();

    long getSequence();

    Comparator<MatchEvent> ORDERING = Comparator
            .comparingLong(MatchEvent::getTick)
            .thenComparingInt(MatchEvent::getPriority)
            .thenComparingLong(MatchEvent::getSequence);

    final class Objective implements MatchEvent {
        private final long tick;
        private final long sequence;

        public Objective(long tick, long sequence) {
            this.tick = tick;
            this.sequence = sequence;
        }

        @Override
        public long getTick() {
            return tick;
        }

        @Override
        public int getPriority() {
            return 0;
        }

        @Override
        public long getSequence() {
            return sequence;
        }

        public Objective copy(long tick, long sequence) {
            return new Objective(tick, sequence);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Objective that)) return false;
            return tick == that.tick && sequence == that.sequence;
        }

        @Override
        public int hashCode() {
            return Objects.hash(tick, sequence);
        }

        @Override
        public String toString() {
            return "Objective(tick=" + tick + ", sequence=" + sequence + ")";
        }
    }

    final class ScoreUpdate implements MatchEvent {
        private final long tick;
        private final long sequence;

        public ScoreUpdate(long tick) {
            this(tick, 0L);
        }

        public ScoreUpdate(long tick, long sequence) {
            this.tick = tick;
            this.sequence = sequence;
        }

        @Override
        public long getTick() {
            return tick;
        }

        @Override
        public int getPriority() {
            return 1;
        }

        @Override
        public long getSequence() {
            return sequence;
        }

        public ScoreUpdate copy(long tick, long sequence) {
            return new ScoreUpdate(tick, sequence);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ScoreUpdate that)) return false;
            return tick == that.tick && sequence == that.sequence;
        }

        @Override
        public int hashCode() {
            return Objects.hash(tick, sequence);
        }

        @Override
        public String toString() {
            return "ScoreUpdate(tick=" + tick + ", sequence=" + sequence + ")";
        }
    }

    final class VictoryCheck implements MatchEvent {
        private final long tick;
        private final long sequence;

        public VictoryCheck(long tick) {
            this(tick, 0L);
        }

        public VictoryCheck(long tick, long sequence) {
            this.tick = tick;
            this.sequence = sequence;
        }

        @Override
        public long getTick() {
            return tick;
        }

        @Override
        public int getPriority() {
            return 2;
        }

        @Override
        public long getSequence() {
            return sequence;
        }

        public VictoryCheck copy(long tick, long sequence) {
            return new VictoryCheck(tick, sequence);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof VictoryCheck that)) return false;
            return tick == that.tick && sequence == that.sequence;
        }

        @Override
        public int hashCode() {
            return Objects.hash(tick, sequence);
        }

        @Override
        public String toString() {
            return "VictoryCheck(tick=" + tick + ", sequence=" + sequence + ")";
        }
    }

    final class DeadlineCheck implements MatchEvent {
        private final long tick;
        private final long sequence;

        public DeadlineCheck(long tick) {
            this(tick, 0L);
        }

        public DeadlineCheck(long tick, long sequence) {
            this.tick = tick;
            this.sequence = sequence;
        }

        @Override
        public long getTick() {
            return tick;
        }

        @Override
        public int getPriority() {
            return 3;
        }

        @Override
        public long getSequence() {
            return sequence;
        }

        public DeadlineCheck copy(long tick, long sequence) {
            return new DeadlineCheck(tick, sequence);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof DeadlineCheck that)) return false;
            return tick == that.tick && sequence == that.sequence;
        }

        @Override
        public int hashCode() {
            return Objects.hash(tick, sequence);
        }

        @Override
        public String toString() {
            return "DeadlineCheck(tick=" + tick + ", sequence=" + sequence + ")";
        }
    }
}
