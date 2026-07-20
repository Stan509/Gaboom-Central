"""
Commande Django de vérification périodique des connexions APK des agents
Exécutée toutes les 5 minutes (via cron/scheduler ou tâche en arrière-plan)
"""
import logging
from django.core.management.base import BaseCommand
from django.utils import timezone
from accounts.models import Agent, AgentStatus, Borlette

logger = logging.getLogger(__name__)


class Command(BaseCommand):
    help = "Vérifie les connexions des APKs agents toutes les 5 minutes et valide la santé de la synchronisation."

    def handle(self, *args, **options):
        now = timezone.localtime(timezone.now())
        self.stdout.write(self.style.SUCCESS(f"[{now.strftime('%Y-%m-%d %H:%M:%S')}] Démarrage de la vérification des connexions APK..."))

        total_agents = Agent.objects.count()
        actifs = Agent.objects.filter(statut=AgentStatus.ACTIF).select_related("user", "borlette")
        
        online_count = 0
        offline_count = 0

        for agent in actifs:
            if agent.is_online:
                online_count += 1
            else:
                offline_count += 1

        borlettes_count = Borlette.objects.count()

        summary_msg = (
            f"Vérification APK terminée : {online_count} agents en ligne, "
            f"{offline_count} hors ligne sur {total_agents} agents total ({borlettes_count} borlettes)."
        )

        logger.info(summary_msg)
        self.stdout.write(self.style.SUCCESS(f"[{now.strftime('%H:%M:%S')}] {summary_msg}"))
