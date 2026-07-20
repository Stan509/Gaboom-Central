import json
from django.test import TestCase, Client
from django.contrib.auth import get_user_model
from accounts.models import Borlette, Agent, AgentStatus, UserRole

User = get_user_model()


class AgentSystemTestCase(TestCase):
    def setUp(self):
        self.client = Client()
        
        # Create Admin User and Borlette
        self.admin_user = User.objects.create_user(
            username="admin_test",
            email="admin@test.com",
            password="adminpassword123",
            role=UserRole.ADMIN,
            is_active=True
        )
        self.borlette = Borlette.objects.create(
            user=self.admin_user,
            nom_borlette="Borlette Test",
            adresse="Port-au-Prince",
            telephone="50937000000",
            slogan="La meilleure borlette"
        )
        
        # Create an Agent via helper
        self.agent_user = User.objects.create_user(
            username="agent_50937112233",
            password="agentpassword123",
            role=UserRole.AGENT,
            is_active=True
        )
        self.agent = Agent.objects.create(
            user=self.agent_user,
            borlette=self.borlette,
            nom="Jean Agent",
            telephone="50937112233",
            zone="Pétion-Ville",
            commission=10.0,
            statut=AgentStatus.ACTIF
        )

    def test_login_by_username(self):
        response = self.client.post(
            "/api/agent/auth/login/",
            data=json.dumps({
                "username": "agent_50937112233",
                "password": "agentpassword123",
                "device_signature": "sig-12345"
            }),
            content_type="application/json"
        )
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertTrue(data.get("success"))
        self.assertIn("tokens", data)

    def test_login_by_phone_number(self):
        # Test logging in using telephone number "50937112233" or "37112233"
        response = self.client.post(
            "/api/agent/auth/login/",
            data=json.dumps({
                "username": "37112233",
                "password": "agentpassword123",
                "device_signature": "sig-67890"
            }),
            content_type="application/json"
        )
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertTrue(data.get("success"))
        self.assertEqual(data["agent"]["nom"], "Jean Agent")

    def test_admin_agent_create_and_redirect(self):
        self.client.force_login(self.admin_user)
        response = self.client.post(
            "/portal/agents/nouveau/",
            data={
                "nom": "Pierre Agent",
                "telephone": "50938998877",
                "zone": "Delmas",
                "mot_de_passe": "newagentpass123",
                "commission": "12.50"
            },
            follow=True
        )
        self.assertEqual(response.status_code, 200)
        # Check that agent was created
        new_agent = Agent.objects.filter(telephone="50938998877").first()
        self.assertIsNotNone(new_agent)
        self.assertEqual(new_agent.statut, AgentStatus.ACTIF)
        self.assertTrue(new_agent.user.is_active)
        
        # Check success message present in response context
        messages = list(response.context["messages"])
        self.assertTrue(any("créé avec succès" in str(m) for m in messages))
