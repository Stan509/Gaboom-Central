import pytest
from django.urls import reverse
from rest_framework import status
from rest_framework.test import APIClient
from django.contrib.auth import get_user_model

User = get_user_model()

@pytest.fixture
def api_client():
    return APIClient()

def test_unauthenticated_api_endpoints_rejected(api_client):
    """
    Vérifie que les requêtes non authentifiées vers les endpoints sensibles sont bloquées (JWT obligatoire).
    """
    endpoints = [
        reverse("agent_api:ticket_create"),
        reverse("agent_api:ticket_create_multi"),
        reverse("agent_api:dashboard"),
        reverse("agent_api:caisse"),
    ]
    for url in endpoints:
        response = api_client.post(url, {}) if "create" in url or "withdraw" in url else api_client.get(url)
        assert response.status_code == status.HTTP_401_UNAUTHORIZED
