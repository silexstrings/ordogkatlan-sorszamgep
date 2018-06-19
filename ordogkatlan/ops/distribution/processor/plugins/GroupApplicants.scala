package ordogkatlan.ops.distribution.processor.plugins

import com.google.inject.Singleton
import ordogkatlan.ops.distribution.model.Applicant

/**
  * a potenciális látogatókat csoportokba szedi és a csoportokat rendezi aszerint, hogy az algoritmus kiket akar
  * inkább kiszolgálni
  */
trait GroupApplicants {

  def apply(applicants: Set[Applicant]):List[Set[Applicant]]

}


/**
  * azokat szolgáljuk ki hamarabb, akiknek eddig kevesebb költése volt
  *
  * "mindenki a lehetőségekhez mérten egyforma értékben kapjon sorszámokat"
  */
@Singleton
class GroupApplicantsBySpent extends GroupApplicants {

  override def apply(applicants: Set[Applicant]):List[Set[Applicant]] =
    applicants
      .groupBy(_.spentCredits)          // csoportosítás költés szerint
      .toList
      .sortBy(_._1)                     // csoportok sorba rendezése költés szerint
      .map(_._2)
}