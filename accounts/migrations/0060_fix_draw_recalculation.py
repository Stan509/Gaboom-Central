from decimal import Decimal
from django.db import migrations

def recalculate_all_with_multi_lot_and_fix_winners(apps, schema_editor):
    from accounts.models import Tirage, Resultat
    from agent_portal.models import Ticket, TicketLine
    from core.services.result_calculation_service import ResultCalculationService

    try:
        # 1. Traiter TOUS les résultats officiels valides avec lots complets
        results = (
            Resultat.objects.exclude(statut="rejected")
            .exclude(lot1="", lot2="", lot3="")
            .select_related("tirage", "tirage__borlette")
            .order_by("date", "id")
        )

        count = 0
        for r in results:
            if not (r.lot1 and r.lot2 and r.lot3):
                continue
            try:
                ResultCalculationService.calculate_gains(
                    tirage=r.tirage,
                    resultat=r,
                )
                count += 1
            except Exception as err:
                print(f"[MIGRATION 0060] Erreur calcul tirage {r.tirage_id} resultat {r.id}: {err}")
        print(f"[MIGRATION 0060] {count} résultats officiels recalculés.")

        # 2. Recalculer individuellement chaque ticket pour garantir sa parfaite synchronisation
        ticket_count = 0
        for ticket in Ticket.objects.all().select_related("tirage").prefetch_related("lignes"):
            try:
                ResultCalculationService.calculate_single_ticket_gains(ticket)
                ticket_count += 1
            except Exception as err:
                print(f"[MIGRATION 0060] Erreur recalcul ticket {ticket.id}: {err}")
        print(f"[MIGRATION 0060] {ticket_count} tickets recalculés et mis à jour.")

    except Exception as e:
        print(f"[MIGRATION 0060] Erreur globale: {e}")

class Migration(migrations.Migration):

    dependencies = [
        ('accounts', '0059_recalculate_multi_lot_gains'),
    ]

    operations = [
        migrations.RunPython(recalculate_all_with_multi_lot_and_fix_winners, migrations.RunPython.noop),
    ]
