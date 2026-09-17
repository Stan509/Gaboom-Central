from django.test import TestCase, Client
from django.urls import reverse
import json
from accounts.models import User, UserRole

class SignupVerificationTests(TestCase):
    def setUp(self):
        self.client = Client()

    def test_admin_signup_and_verify_code(self):
        # 1. Sign up
        payload = {
            "username": "directeur_test",
            "email": "dir@example.com",
            "phone": "+50933333333",
            "password": "securepassword123",
            "borlette_name": "Test Borlette",
            "adresse": "Port-au-Prince",
            "slogan": "The Best",
        }
        response = self.client.post(
            reverse("accounts_api:signup"),
            data=json.dumps(payload),
            content_type="application/json"
        )
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertTrue(data["success"])
        
        # Verify user is created but inactive
        user = User.objects.get(username="directeur_test")
        self.assertFalse(user.is_active)
        self.assertFalse(user.is_email_verified)
        self.assertEqual(user.role, UserRole.ADMIN)
        
        # Get verification code
        code = user.email_verification_token
        self.assertTrue(code.isdigit())
        self.assertEqual(len(code), 6)

        # 2. Verify with wrong code
        verify_payload = {
            "username": "directeur_test",
            "code": "000000"
        }
        verify_response = self.client.post(
            reverse("accounts_api:signup_verify_code"),
            data=json.dumps(verify_payload),
            content_type="application/json"
        )
        self.assertEqual(verify_response.status_code, 400)
        self.assertFalse(verify_response.json()["success"])

        # 3. Verify with correct code
        verify_payload["code"] = code
        verify_response = self.client.post(
            reverse("accounts_api:signup_verify_code"),
            data=json.dumps(verify_payload),
            content_type="application/json"
        )
        self.assertEqual(verify_response.status_code, 200)
        verify_data = verify_response.json()
        self.assertTrue(verify_data["success"])
        self.assertEqual(verify_data["data"]["redirect_url"], "/portal/dashboard/")

        # Verify user is now active
        user.refresh_from_db()
        self.assertTrue(user.is_active)
        self.assertTrue(user.is_email_verified)
        self.assertIsNone(user.email_verification_token)


class AuditSousDirecteurAndVipTests(TestCase):
    def setUp(self):
        from decimal import Decimal
        from django.utils import timezone
        from accounts.models import (
            Borlette,
            SousDirecteur,
            SousDirecteurTiragePreference,
            Agent,
            Subscription,
            GlobalPaymentSettings,
            Tirage,
            TirageStatus,
        )

        # 1. Create Admin & Borlette
        self.admin_user = User.objects.create_user(
            username="test_admin_borlette",
            password="password123",
            role=UserRole.ADMIN,
        )
        self.borlette = Borlette.objects.create(
            user=self.admin_user,
            nom_borlette="Borlette Centrale",
            is_vip=False,
            vip_lifetime_free=False,
        )

        # 2. Create Sous-Directeur with 14% commission
        self.sd_user = User.objects.create_user(
            username="sd_marc",
            password="password123",
            role=UserRole.SOUS_DIRECTEUR,
        )
        self.sd = SousDirecteur.objects.create(
            user=self.sd_user,
            borlette=self.borlette,
            nom="Marc Antoine",
            commission_percent=Decimal("14.00"),
            is_active=True,
        )

        # 3. Create Agent under Sous-Directeur with 10% commission
        self.agent_user = User.objects.create_user(
            username="pos_agent1",
            password="password123",
            role=UserRole.AGENT,
        )
        self.agent = Agent.objects.create(
            user=self.agent_user,
            borlette=self.borlette,
            sous_directeur=self.sd,
            nom="Agent 1",
            commission=Decimal("10.00"),
            statut="ACTIF",
        )

        # 4. Create Tirages
        self.tirage1 = Tirage.objects.create(
            borlette=self.borlette,
            nom="New York Midi",
            type="midi",
            statut=TirageStatus.ACTIF,
        )
        self.tirage2 = Tirage.objects.create(
            borlette=self.borlette,
            nom="New York Soir",
            type="soir",
            statut=TirageStatus.ACTIF,
        )

    def test_sous_directeur_margin_and_agent_relation(self):
        from decimal import Decimal

        # Agent is linked to SousDirecteur
        self.assertEqual(self.agent.sous_directeur, self.sd)
        self.assertEqual(self.sd.agents.count(), 1)

        # SousDirecteur margin: 14% - 10% = 4%
        margin = self.sd.commission_percent - self.agent.commission
        self.assertEqual(margin, Decimal("4.00"))

    def test_sous_directeur_tirage_preference(self):
        from accounts.models import SousDirecteurTiragePreference

        # Deactivate tirage2 for this sous-directeur
        pref = SousDirecteurTiragePreference.objects.create(
            sous_directeur=self.sd,
            tirage=self.tirage2,
            actif=False,
        )
        self.assertFalse(pref.actif)

        # Tirage 1 is active (no preference or pref.actif=True)
        sd_disabled = set(
            SousDirecteurTiragePreference.objects.filter(
                sous_directeur=self.sd,
                actif=False,
            ).values_list("tirage_id", flat=True)
        )
        self.assertIn(self.tirage2.id, sd_disabled)
        self.assertNotIn(self.tirage1.id, sd_disabled)

    def test_borlette_vip_and_lifetime_free(self):
        from decimal import Decimal
        from django.utils import timezone
        from datetime import timedelta
        from accounts.models import Subscription, SubscriptionType

        sub = Subscription.objects.create(
            user=self.admin_user,
            borlette=self.borlette,
            subscription_type=SubscriptionType.STANDARD,
            end_date=timezone.now().date() + timedelta(days=30),
            is_active=True,
        )

        # Default borlette owes 1250 * 1 agent = 1250
        amount_due = sub.calculate_amount_due()
        self.assertEqual(amount_due, Decimal("1250.00"))

        # Mark as lifetime free VIP
        self.borlette.is_vip = True
        self.borlette.vip_lifetime_free = True
        self.borlette.save()

        # Lifetime free VIP owes 0
        amount_due_vip = sub.calculate_amount_due()
        self.assertEqual(amount_due_vip, Decimal("0.00"))

    def test_global_payment_settings_profit_reset(self):
        from accounts.models import GlobalPaymentSettings
        from django.utils import timezone

        settings, _ = GlobalPaymentSettings.objects.get_or_create(id=1)
        self.assertIsNone(settings.accumulated_profit_reset_date)

        # Reset profit accumulator
        now = timezone.now()
        settings.accumulated_profit_reset_date = now
        settings.save()

        settings.refresh_from_db()
        self.assertIsNotNone(settings.accumulated_profit_reset_date)

