package ordogkatlan.ops.distribution.processor

sealed trait DistributionMode

case object Primary extends DistributionMode
case object Secondary extends DistributionMode
