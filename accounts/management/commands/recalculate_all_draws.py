from __future__ import annotations

import logging
from decimal import Decimal
from django.core.management.base import BaseCommand
from django.utils import timezone
from django.db import transaction

from accounts.models import Resultat, Tirage
from agent_portal.models import Ticket, TicketStatus
from core.services.result_calculation_service import ResultCalculationService

logger = logging.getLogger(__name__)


class Command(BaseCommand):
    help = "Recalcule tous les tirages et tickets gagnants (en ligne et hors ligne) pour tous les tirages avec résultats existants."

    def add_arguments(self, parser):
        parser.add_argument(
            "--tirage-id",
            type=int,
            help="Recalculer uniquement pour un tirage spécifique (ID)",
        )
        parser.add_argument(
            "--date",
            type=str,
            help="Recalculer uniquement pour une date spécifique (YYYY-MM-DD)",
        )

    def handle(self, *args, **options):
        self.stdout.write(self.style.NOTICE("=== DÉBUT DU RECALCUL DES TIRAGES ET GAINS ==="))

        # Traiter tous les résultats officiels valides avec lots complets
        qs = (
            Resultat.objects.exclude(statut="rejected")
            .exclude(lot1="", lot2="", lot3="")
            .select_related("tirage", "tirage__borlette")
            .order_by("date", "id")
        )

        if options.get("tirage_id"):
            qs = qs.filter(tirage_id=options["tirage_id"])
        if options.get("date"):
            qs = qs.filter(date=options["date"])

        total_results = qs.count()
        self.stdout.write(f"Nombre de résultats officiels à traiter : {total_results}")

        total_tickets_recalculated = 0
        total_winners_found = 0
        grand_total_gains = Decimal("0.00")

        for r in qs:
            tirage = r.tirage
            if not (r.lot1 and r.lot2 and r.lot3):
                continue
            self.stdout.write(
                f"\nTraitement résultat ID={r.id}: {tirage.nom} ({r.date}) - Lots: {r.lot1}-{r.lot2}-{r.lot3}"
            )
            try:
                stats = ResultCalculationService.calculate_gains(
                    tirage=tirage,
                    resultat=r,
                )
                self.stdout.write(
                    self.style.SUCCESS(
                        f"  -> Tickets: {stats['tickets_count']}, Gagnants: {stats['winners_count']}, Gains dus: {stats['total_gain_du']} HTG"
                    )
                )
                total_tickets_recalculated += stats["tickets_count"]
                total_winners_found += stats["winners_count"]
                grand_total_gains += stats["total_gain_du"]
            except Exception as e:
                self.stdout.write(
                    self.style.ERROR(f"  -> Erreur lors du calcul pour Resultat {r.id}: {e}")
                )

        self.stdout.write("\n" + "=" * 50)
        self.stdout.write(
            self.style.SUCCESS(
                f"RECALCUL TERMINÉ AVEC SUCCÈS:\n"
                f" - Résultats traités: {total_results}\n"
                f" - Tickets recalculés: {total_tickets_recalculated}\n"
                f" - Tickets gagnants identifiés: {total_winners_found}\n"
                f" - Total gains dus: {grand_total_gains} HTG"
            )
        )
