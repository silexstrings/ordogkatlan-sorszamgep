package ordogkatlan.ops.distribution.processor.plugins

import ordogkatlan.ops.distribution.model.Applicant

import scala.math.Ordering.Double.IeeeOrdering

/**
  * a potenciális látogatókat csoportokba szedi és a csoportokat rendezi aszerint, hogy az algoritmus kiket akar
  * inkább kiszolgálni
  */

object GroupApplicants {

  /**
    * azokat szolgáljuk ki hamarabb, akiknek eddig kevesebb költése volt
    *
    * "mindenki a lehetőségekhez mérten egyforma értékben kapjon sorszámokat"
    */
  def apply(applicants: Set[Applicant]): List[Set[Applicant]] =
    applicants
      .groupBy(_.spentCredits)          // csoportosítás költés szerint
      .toList
      .sortBy(_._1)                    // csoportok sorba rendezése költés szerint
      .map(_._2)
}
