# A XI. Ördögkatlan fesztivál sorszámgépének kiosztási algoritmusa

[belépési pont](https://github.com/silexstrings/ordogkatlan-sorszamgep/blob/master/ordogkatlan/ops/distribution/processor/Calculator.scala#L31)

Eggyel felhasználóközpontúbb leírás: [Ördögkatlan Sorszámgép](http://www.ordogkatlan.hu/2017/07/itt-az-ordogkatlan-sorszamgep.html)

## Időzítés, az algoritmus futása

 * Egy adott célnapra először midnig előző este fut először az algoritmus
 * Ezután 5 percenként ellenőrzi, hogy van-e sorszám, ami a célnapra kiadható (pl. mert visszaadták), ha van, újra lefut ![Ördögkalan](http://messzevan.hu/oklogo.jpg)

## Leírás nagyléptékben

A lenti leírás nagy része a forráskódban fellelhető kommentekben is ott van, ez az áttekintés a tájékozódást hivatott segíteni:


 1. Összegyűjtjük a felhasználókat és a kívánságlistájukat, akiknek van a célnapon teljesíthető kívánsága ([adatforrás](https://github.com/silexstrings/ordogkatlan-sorszamgep/blob/master/ordogkatlan/ops/distribution/ds/CalculatorDataSource.scala#L93))
 2. Elsődleges kiosztás esetén kiszámoljuk az érintett előadások sorszámainak értékességét, másodlagos kiosztásoknál a már kiszámolt értéket fogjuk használni ([kalkuláció](https://github.com/silexstrings/ordogkatlan-sorszamgep/blob/master/ordogkatlan/ops/distribution/processor/Calculator.scala#L31))
 3. A pályázó látogatók közül kiszűrjük, akiknek nem maradt teljesíthető kívánságuk ([szűrés](https://github.com/silexstrings/ordogkatlan-sorszamgep/blob/master/ordogkatlan/ops/distribution/processor/plugins/FilterFulfillable.scala#L19))
 4. A pályázó látogatókat csoportokba osztjuk az alapján, hogy eddig mekkora értékben kaptak sorszámot, a csoportokat növekvő sorrendbe állítjuk ([csoportosítás](https://github.com/silexstrings/ordogkatlan-sorszamgep/blob/master/ordogkatlan/ops/distribution/processor/plugins/GroupApplicants.scala#L25))
 5. Az egyes csoportok tagjait véletlenszerűen sorbaállítjuk ([egyenrangúak sorbaállítása](https://github.com/silexstrings/ordogkatlan-sorszamgep/blob/master/ordogkatlan/ops/distribution/processor/plugins/OrderApplicants.scala#L30))
 6. Aktuális prioritásnak beállítjuk a még nem érintett, legmagasabbat
 7. Végigmegyünk csoportonként, azon belül a fenti véletlenszerű sorrend szerint a látogatókon ([iteráció](https://github.com/silexstrings/ordogkatlan-sorszamgep/blob/master/ordogkatlan/ops/distribution/processor/Calculator.scala#L122))
 8. teljesítjük az aktuális prioritáson lévő kívánságát, ha az a célnapra szól, és van még szabad sorszám. Ha tehetjük, az összes igényelt sorszámot odaadjuk, ha nem, akkor csak annyit, amennyit tudunk. ([teljesítés](https://github.com/silexstrings/ordogkatlan-sorszamgep/blob/master/ordogkatlan/ops/distribution/processor/plugins/WishFulfiller.scala#L16))
 9. Ha még van nem érintett prioritás és még van kiosztható sorszám, visszalépünk a **3**-mas pontra.


## Magyarázatok, pontosítások

### célnap
a futás során az ezen a napon játszott előadások vehetők csak figyelembe

### felhasználó, akinek van a célnapon teljesíthető kívánsága

* nem diszkvalifikált, azaz:
  * legfeljebb egy napon volt át nem vett, megkapott, de vissza nem adott kívánsága, amit elsődleges kiosztáson kapott
* van legalább egy kívánsága a célnapra, ami kiszolgálható, azaz:
  * nem elmaradó előadásra szól
  * még van ideje átvenni a sorszámot, ha megkapja
  * nincs átfedésben más, már megkapott sorszámmal
  * nincs két különböző, a célnapon játszott előadásra sorszáma

### sorszám átvételi időkorlát
Az előadás kezdete előtt másfél órával, de legkésőbb este 6-kor át kell venni a sorszámokat

### sorszám kioszthatósági időkorlát
Az átvételi időkorlát előtt legkésőbb egy órával osztható ki egy sorszám

### sorszámok/előadások átfedése
Két különböző előadásra szóló sorszám akkor tekintendő átfedőnek, ha a korábbi előadás vége és a későbbi előadás kezdete között nem telik el legalább egy óra

### elsődleges kiosztás
Az előző nap esti, első nekifutás

### másodlagos kiosztás(ok)
A visszaadott sorszámok ezeken tudnak kiosztásra kerülni. Ez az egyes előadások időkorlátait figyelembe véve bármeddig történhet

### előadás sorszámának értékessége
Hány sorszámot nem kapott meg más, hogy én kapjak egyet?

```
(kért sorszámok / rendelkezésre álló sorszámok) - 1
```

### prioritás
1-10 közötti szám, a kívánság helye a kívánságlistán. Alacsonyabb szám magasabb prioritást (nagyobb fontosságot) jelez.
