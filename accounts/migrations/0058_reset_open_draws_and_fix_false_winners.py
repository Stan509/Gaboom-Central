from decimal import Decimal
from django.db import migrations

def fix_false_winners_and_recalculate(apps, schema_editor):
    from accounts.models import Tirage, Resultat
    from agent_portal.models import Ticket, TicketLine
    from core.services.result_calculation_service import ResultCalculationService

    try:
        # 1. Identifier les tirages actuellement OUVERTS
        open_tirage_ids = [t.id for t in Tirage.objects.all() if t.etat_ouverture == "OUVERT"]
        open_tickets = Ticket.objects.filter(tirage_id__in=open_tirage_ids)
        open_tickets_count = open_tickets.count()
        if open_tickets_count > 0:
            open_tickets.update(is_winner=False, total_gain_du=Decimal("0.00"), computed_at=None)
            TicketLine.objects.filter(ticket_id__in=open_tickets.values_list("id", flat=True)).update(
                gain_du=Decimal("0.00"), is_winner=False, win_context=""
            )
            print(f"[MIGRATION 0058] {open_tickets_count} tickets de tirages ouverts réinitialisés à 0 gain.")

        # 2. Recalculer proprement les gains pour les tirages FERMÉS avec des résultats valides
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
                print(f"[MIGRATION 0058] Erreur calcul tirage {r.tirage_id} resultat {r.id}: {err}")

    except Exception as e:
        print(f"[MIGRATION 0058] Erreur globale: {e}")

class Migration(migrations.Migration):

    dependencies = [
        ('accounts', '0057_recalculate_all_draws'),
    ]

    operations = [
        migrations.RunPython(fix_false_winners_and_recalculate, migrations.RunPython.noop),
    ]
