from django.db import migrations

def recalculate_draws(apps, schema_editor):
    try:
        from accounts.models import Resultat
        from core.services.result_calculation_service import ResultCalculationService
        for r in Resultat.objects.all().select_related("tirage", "tirage__borlette"):
            try:
                ResultCalculationService.calculate_gains(
                    tirage=r.tirage,
                    resultat=r,
                )
            except Exception as e:
                print(f"[MIGRATION 0057] Error recalculating draw {r.id}: {e}")
    except Exception as e:
        print(f"[MIGRATION 0057] General error: {e}")

class Migration(migrations.Migration):

    dependencies = [
        ('accounts', '0056_borlette_is_vip_borlette_vip_lifetime_free_and_more'),
    ]

    operations = [
        migrations.RunPython(recalculate_draws, migrations.RunPython.noop),
    ]
