package dev.jlo.gamemodes.domain.common;

import java.util.Objects;

public sealed interface Ownership permits Ownership.Queue, Ownership.Match {
    final class Queue implements Ownership {
        private final String id;

        public Queue(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public Queue copy(String id) {
            return new Queue(id);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Queue queue)) return false;
            return Objects.equals(id, queue.id);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(id);
        }

        @Override
        public String toString() {
            return "Queue(id=" + id + ")";
        }
    }

    final class Match implements Ownership {
        private final String id;

        public Match(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public Match copy(String id) {
            return new Match(id);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Match match)) return false;
            return Objects.equals(id, match.id);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(id);
        }

        @Override
        public String toString() {
            return "Match(id=" + id + ")";
        }
    }
}
