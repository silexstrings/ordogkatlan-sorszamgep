package ordogkatlan.ops.distribution.model

import java.time.LocalDateTime

object Overlaps {

  implicit class IntervalHasOverlaps(a: (LocalDateTime, LocalDateTime)) {

    def overlaps(b: (LocalDateTime, LocalDateTime)): Boolean = {
      val (startA, endA) = a
      val (startB, endB) = b

      ! {
        ((startA isBefore startB) && (endA isBefore startB)) ||
        ((startA isAfter endB) && (endA isAfter endB))
      }
    }
  }

}
