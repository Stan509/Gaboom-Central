import time
import pytest
from django.urls import reverse
from rest_framework import status
from rest_framework.test import APIClient
from django.db import connection

@pytest.fixture
def api_client():
    return APIClient()

@pytest.mark.django_db
def test_query_count_baseline(api_client, django_assert_max_num_queries):
    """
    Mesure le nombre de requêtes SQL exécutées lors de l'accès aux endpoints de base (N+1 Query baseline).
    """
    # Dans la Phase 0, on initialise la mesure
    url = reverse("agent_api:health")
    
    with django_assert_max_num_queries(5):
        response = api_client.get(url)
        assert response.status_code == status.HTTP_200_OK

@pytest.mark.django_db
def test_response_time_baseline(api_client):
    """
    Mesure le temps de réponse d'un endpoint et vérifie qu'il respecte la baseline de performance.
    """
    url = reverse("agent_api:health")
    
    start_time = time.time()
    response = api_client.get(url)
    elapsed_time = (time.time() - start_time) * 1000 # en millisecondes
    
    assert response.status_code == status.HTTP_200_OK
    # Baseline théorique de démarrage en local : < 150ms
    assert elapsed_time < 150, f"Temps de réponse élevé mesuré : {elapsed_time:.2f}ms"
