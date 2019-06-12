package ordogkatlan.ops.distribution.ds

import java.time.LocalDate

import io.jvm.uuid._
import ordogkatlan.ops.distribution.model.{Applicant, TicketablePlay}

import scala.concurrent.Future

trait CalculatorDataSource {

  /**
    * az adott kiosztás során figyelembe vehető látogatók halmaza, azaz
    * minden látogató, aki:
    *   * nem diszkvalifikált
    *     * azaz nem volt első körben megnyert, át nem vett de vissza nem adott kívánsága
    *   * van legalább egy kívánsága a célnapra, ami kiszolgálható, azaz:
    *     * nem elmaradó előadásra szól
    *     * még van ideje átvenni a sorszámot, ha megkapja
    *     * nincs átfedésben más, már megkapott sorszámmal
    *   * nem kapott egy előadásnál többre sorszámot a célnapon
    */
  def applicants(targetDay: LocalDate): Future[Set[Applicant]]

  /**
    * az aktuális kiosztás során érintett előadások tára
    */
  def detailedPlays(applicants: Set[Applicant]): Future[Map[UUID, TicketablePlay]]

}