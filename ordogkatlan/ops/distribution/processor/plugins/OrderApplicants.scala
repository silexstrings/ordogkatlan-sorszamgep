package ordogkatlan.ops.distribution.processor.plugins

import com.google.inject.Singleton
import ordogkatlan.ops.distribution.model.Applicant

import scala.util.Random

/**
  * sorba rendezi a GroupApplicants szerint egy szinten kiszolgálható látogatókat
  * ez a sorrend fogja meghatározni, kinek teljesítjük hamarabb a kívánságát az egy szinten lévő látogatók
  * közül
  */
trait OrderApplicants {

  def apply(applicants: Set[Applicant]):List[Applicant]
}


/**
  * véletlenszerű sorbarendezés
  *
  * "egyenrangú vetélytársak közül véletlenszerűen választunk, amíg el nem fogynak az odaadható sorszámok, vagy
  *  a sorban álló látogatók"
  */
@Singleton
class RandomOrderApplicants extends OrderApplicants {

  private val randomizer:Random = new Random(System.currentTimeMillis)

  override def apply(applicants: Set[Applicant]): List[Applicant] = {
    randomizer.setSeed(randomizer.nextLong)
    applicants.toList.zip((1 to applicants.size).map(_ => randomizer.nextInt))
      .sortBy(_._2)
      .map(_._1)
  }

}