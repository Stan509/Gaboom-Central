from decimal import Decimal
from django.db import migrations

def recalculate_all_with_multi_lot(apps, schema_editor):
    from accounts.models import Tirage, Resultat
    from agent_portal.models import Ticket, TicketLine
    from core.services.result_calculation_service import ResultCalculationService

    try:
        # 1. Identifier les tirages actuellement OUVERTS et réinitialiser
        open_tirage_ids = [t.id for t in Tirage.objects.all() if t.etat_ouverture == "OUVERT"]
        open_tickets = Ticket.objects.filter(tirage_id__in=open_tirage_ids)
        open_tickets.update(is_winner=False, total_gain_du=Decimal("0.00"), computed_at=None)
        TicketLine.objects.filter(ticket_id__in=open_tickets.values_list("id", flat=True)).update(
            gain_du=Decimal("0.00"), is_winner=False, win_context=""
        )

        # 2. Recalculer proprement tous les tirages FERMÉS avec résultats officiels
        closed_results = (
            Resultat.objects.exclude(tirage_id__in=open_tirage_ids)
            .exclude(statut="rejected")
            .exclude(lot1="", lot2="", lot3="")
            .select_related("tirage", "tirage__borlette")
            .order_by("date", "id")
        )

        for r in closed_results:
            if not (r.lot1 and r.lot2 and r.lot3):
                continue
            try:
                ResultCalculationService.calculate_gains(
                    tirage=r.tirage,
                    resultat=r,
                )
            except Exception as err:
                print(f"[MIGRATION 0059] Erreur calcul tirage {r.tirage_id} resultat {r.id}: {err}")

    except Exception as e:
        print(f"[MIGRATION 0059] Erreur globale: {e}")

class Migration(migrations.Migration):

    dependencies = [
        ('accounts', '0058_reset_open_draws_and_fix_false_winners'),
    ]

    operations = [
        migrations.RunPython(recalculate_all_with_multi_lot, migrations.RunPython.noop),
    ]
