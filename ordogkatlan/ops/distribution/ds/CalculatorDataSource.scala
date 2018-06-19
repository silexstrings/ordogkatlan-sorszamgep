package ordogkatlan.ops.distribution.ds

import com.google.inject.{Inject, Singleton}
import io.jvm.uuid._
import ordogkatlan.models._
import ordogkatlan.ops.distribution.model.{Applicant, CalculableWish, TicketablePlay}
import ordogkatlan.ops.distribution.processor.Calculator
import ordogkatlan.utils.slick.{HasDatabaseConfig, PgProfile, PortableJodaSupport}
import org.joda.time.{DateTime, LocalDate}
import slick.basic.DatabaseConfig
import slick.jdbc.GetResult

import scala.concurrent.{ExecutionContext, Future}

sealed trait CalculatorDataSource {

  /**
    * az adott kiosztás során figyelembe vehető látogatók halmaza, azaz
    * minden látogató, aki:
    *   * nem diszkvalifikált
    *     * legfeljebb egy napon volt át nem vett, megkapott, de vissza nem adott kívánsága,
    *       amit elsődleges kiosztáson kapott
    *   * van legalább egy kívánsága a célnapra, ami kiszolgálható, azaz:
    *     * nem elmaradó előadásra szól
    *     * még van ideje átvenni a sorszámot, ha megkapja
    *     * nincs átfedésben más, már megkapott sorszámmal
    *   * nem kapott egy előadásnál többre sorszámot a célnapon
    */
  def applicants(targetDay: LocalDate):Future[Set[Applicant]]

  /**
    * az aktuális kiosztás során érintett előadások tára
    */
  def detailedPlays(applicants: Set[Applicant]):Future[Map[UUID, TicketablePlay]]

  /**
    * lementi az előadás egy jegyének az értékét
    * későbbi, másodlagos kiosztások alatt is azt az értéket akarjuk figyelembe venni, amit az elsődleges kiosztás során
    * kalkuláltunk
    */
  def saveTicketPrice(play: TicketablePlay):Future[Int]

}

/**
  * adatbáziskapcsolat
  */
@Singleton
class PersistentDataSource @Inject() (
  val wishDAO: WishDAO,
  val visitorDAO: VisitorDAO,
  val playDAO: PlayDAO,
  val reservationDAO: ReservationDAO,
  val dbConfig: DatabaseConfig[PgProfile],
  val jodaSupport: PortableJodaSupport
)(implicit ec:ExecutionContext) extends CalculatorDataSource with HasDatabaseConfig[PgProfile] {

  import dbConfig.profile.api._
  import jodaSupport._

  val wishes = wishDAO.wishes
  val visitors = visitorDAO.visitors
  val plays = playDAO.plays
  val reservations = reservationDAO.reservations

  //scalastyle:off method.length
  /**
    * az adott kiosztás során figyelembe vehető látogatók halmaza
    */
  override def applicants(targetDay: LocalDate): Future[Set[Applicant]] = {

    /**
      * az adott látogató diszkvalifikált-e, azaz
      * van-e legalább két különböző napon volt át nem vett, megkapott, de vissza nem adott kívánsága,
      * amit elsődleges kiosztáson kapott
      */
    def disqualified(applicant: Applicant):Boolean =
      applicant.wishlist.flatMap(_.disqualifierAt.map(_.toString)).toSet.size > 1

    /**
      * kaphat-e még az adott látogató az adott napon valamit, azaz
      * nem kapott-e már legalább két különböző előadásra
      */
    def canStillWin(applicant: Applicant, targetDay: LocalDate):Boolean =
      applicant.wishlist.count(w => w.playDay.isEqual(targetDay) && w.wonSeats > 0) < 2

    implicit val getResult:GetResult[(UUID, UUID, Int, Int, UUID, Int, Float, Option[LocalDate], LocalDate)] =
      GetResult { r => (
        r.nextUUID, r.nextUUID, r.nextInt, r.nextInt, r.nextUUID, r.nextInt, r.nextFloat,
        r.nextTimestampOption().map(new LocalDate(_)), new LocalDate(r.nextTimestamp())
      )}

    val query =
      sql"""
        |select distinct
        |  w.visitor_id,
        |  w.wish_id, coalesce(w.won_seats, 0) as won_seats, w.desired_seats, w.play_id, w.priority,
        |  p.credits_per_ticket * coalesce(w.won_seats, 0) as spent_credits,
        |  case when
        |    w.is_first_round = true and
        |    coalesce(w.won_seats, 0) > 0 and
        |    coalesce(r.is_retrieved, false) and
        |    p.is_canceled = false
        |  then p.start::date else null
        |  end as disqualifier_at,
        |  p.start::date as play_day
        |from
        |  wishes w
        |  natural join plays p
        |  left join reservations r on w.reservation_id = w.reservation_id
        |where
        |  exists (         -- van a látogatónak kapható kívánsága
        |    select
        |      1
        |    from
        |      wishes w1
        |      natural join plays p1
        |    where
        |     w1.visitor_id = w.visitor_id and
        |     p1.reservable_seats > 0 and                 -- van beállított kapható sorszáma
        |     p1.is_canceled = false and                  -- nem elmaradó
        |     p1.start::date = $targetDay                 -- célnapi előadás
        |  )
        | order by
        |   w.visitor_id,         -- csoportosítás látogatónként
        |   w.priority asc        -- rendezés fontossági sorrend szerint
      """.stripMargin.as[(UUID, UUID, Int, Int, UUID, Int, Float, Option[LocalDate], LocalDate)]

    //egy sor egy látogató-kívánság pár

    db.run(query).map(_.groupBy(_._1).map { //csoportosítunk látogató szerint
      case (visitorId, contents) =>
        Applicant(
          visitorId = visitorId,
          wishlist = contents.map {  // a kívánságok
            case (_, wishId, wonSeats, desiredSeats, playId, priority, _, disqualifierAt, playDay) => CalculableWish(
              wishId = wishId,
              wonSeats = wonSeats,
              desiredSeats = desiredSeats,
              ticketablePlayId = playId,
              priority = priority,
              disqualifierAt = disqualifierAt,
              playDay = playDay
            )
          }.toList.sortBy(_.priority),
          spentCredits = contents.map(_._7).sum
        )
    }.toSet // tehát néhány sorból egy látogató és a hozzá tartozó kívánságok jönnek létre

    //amiből még kiszűrünk néhány látogatót bizonyos feltételek szerint
    .filter(applicant => !disqualified(applicant) && canStillWin(applicant, targetDay)))
  }

  /**
    * az aktuális kiosztás során érintett előadások tára, azaz
    * az összes olyan előadás, amelyik legalább az egyik érintett látogató kívánságlistáján szerepel
    */
  override def detailedPlays(applicants: Set[Applicant]): Future[Map[UUID, TicketablePlay]] = {
    val playIds = applicants.flatMap(_.wishlist.map(_.ticketablePlayId))

    implicit val getResult:GetResult[(UUID, Int, Int, DateTime, DateTime, Boolean, Option[Float], String)] =
      GetResult { r => (
        r.nextUUID, r.nextInt, r.nextInt, new DateTime(r.nextTimestamp), new DateTime(r.nextTimestamp), r.nextBoolean,
        r.nextFloatOption, r.nextString
      )}

    val query =
      sql"""
        |select
        |  p.play_id,
        |
        |  case bool_and(p.is_canceled) when true then 0
        |  else coalesce(min(p.reservable_seats) - sum(w.won_seats), min(p.reservable_seats), 0) end
        |    as distributable_seats,
        |
        |  case bool_and(p.is_canceled) when true then 0
        |  else min(p.reservable_seats) end
        |    as reservable_seats,
        |
        |  p.start,
        |  p.end_time,
        |  p.is_canceled,
        |  p.credits_per_ticket,
        |  p.title
        |from
        |  wishes w
        |  join plays p on w.play_id = p.play_id
        |where
        |  p.play_id in (#${playIds.map("'" + _ + "'").mkString(", ")})
        |group by
        |  p.play_id, p.start, p.end_time, p.is_canceled, p.credits_per_ticket, p.title
      """.stripMargin.as[(UUID, Int, Int, DateTime, DateTime, Boolean, Option[Float], String)]

    db.run(query).map(_.map{
      case (playId, distributableTickets, reservableSeats, start, end, isCanceled, creditsPerTicket, title) =>
        playId -> TicketablePlay(
          playId = playId,
          distributableSeats = distributableTickets,
          start = start,
          end = end,
          isCanceled = isCanceled,
          //ha már számoltunk és mentettünk sorszámértéket korábban, egyébként
          ticketPrice = creditsPerTicket getOrElse {
            // kiszámoljuk most
            Calculator.creditsPerTicket(
              //szabad helyek az előadásra
              seats = reservableSeats,
              //kívánt helyek az előadásra
              wishes = applicants.toList.map(_.wishlist.filter(_.ticketablePlayId == playId).map(_.desiredSeats).sum).sum
            )
          },
          title = title,
          allReservableSeats = reservableSeats
        )
    }.toMap)
  }
  //scalastyle:on method.length

  override def saveTicketPrice(play: TicketablePlay): Future[Int] = db.run {
    playDAO.plays.filter(_.playId === play.playId).map(_.creditsPerTicket).update(Some(play.ticketPrice))
  }
}