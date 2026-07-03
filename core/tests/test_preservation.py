import pytest
from django.db import connection
from django.contrib.auth import get_user_model
from core.feature_flags import is_feature_enabled

User = get_user_model()

@pytest.mark.django_db
def test_user_model_columns_preservation():
    """
    Vérifie que les colonnes et relations critiques de l'utilisateur sont préservées.
    """
    # Vérifie l'existence des champs sur le modèle User
    user_fields = [f.name for f in User._meta.get_fields()]
    assert "role" in user_fields
    assert "active_session_id" in user_fields
    assert "device_signature" in user_fields

@pytest.mark.django_db
def test_feature_flags_disabled_by_default():
    """
    Vérifie que tous les feature flags de la Phase 0 sont désactivés par défaut.
    """
    flags = [
        "OFFLINE_V2", "SYNC_ENGINE_V2", "QUEUE_ENGINE", "LOTTERY_CLOCK",
        "SQLCIPHER", "GO_GATEWAY", "RUST_SIGNATURE", "DELTA_SYNC",
        "PRIORITY_QUEUE", "ANTI_REPLAY"
    ]
    for flag in flags:
        assert is_feature_enabled(flag) is False

@pytest.mark.django_db
def test_critical_database_tables_exist():
    """
    Vérifie que les tables relationnelles critiques sont présentes en base.
    """
    tables = connection.introspection.table_names()
    critical_tables = [
        "accounts_user",
        "accounts_borlette",
        "accounts_agent",
        "accounts_tirage",
        "accounts_resultat",
        "agent_portal_ticket",
        "agent_portal_ticketline",
        "agent_portal_agentledgerentry",
        "agent_portal_ticketidentity",
    ]
    for table in critical_tables:
        assert table in tables

@pytest.mark.django_db
def test_ticket_identity_fields():
    """
    Vérifie la création et la structure des champs d'un enregistrement TicketIdentity.
    """
    from agent_portal.models import TicketIdentity
    import uuid

    t_uuid = uuid.uuid4()
    t_identity = TicketIdentity.objects.create(
        global_uuid=t_uuid,
        device_id="device-test-123",
        origin_station="station-pos-456",
        sequence_number=1001,
        signature_version=1,
        game_session="session-ny-evening-2026",
    )

    assert t_identity.global_uuid == t_uuid
    assert t_identity.sync_status == TicketIdentity.SyncStatus.DRAFT
    assert t_identity.origin_station == "station-pos-456"
    assert t_identity.sequence_number == 1001
    assert t_identity.game_session == "session-ny-evening-2026"

