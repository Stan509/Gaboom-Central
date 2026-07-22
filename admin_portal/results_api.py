"""
API pour les résultats de tirage et recherche de tickets (Phase B).
"""
import json
from datetime import timedelta
from decimal import Decimal, InvalidOperation

from django.contrib.auth.decorators import login_required
from django.db import models, transaction
from django.http import HttpRequest, JsonResponse, HttpResponse
from django.utils import timezone
from django.views.decorators.http import require_http_methods, require_POST

from accounts.models import Tirage, Resultat, UserRole, AuditAction
from accounts.audit import log_audit
from agent_portal.models import Ticket, TicketStatus, TicketPayout
from agent_portal.services import TicketPayoutService
from core.services.result_calculation_service import ResultCalculationService

from admin_portal.security import get_user_borlette, staff_can


def _require_admin_api(request: HttpRequest) -> JsonResponse | None:
    """Vérifie que l'utilisateur est admin avec borlette."""
    if not request.user.is_authenticated:
        return JsonResponse({"error": "Non authentifié"}, status=401)
    if request.user.role != UserRole.ADMIN:
        return JsonResponse({"error": "Accès refusé"}, status=403)
    borlette = get_user_borlette(request.user)
    if not borlette:
        return JsonResponse({"error": "Pas de borlette associée"}, status=403)
    return None


@login_required
def api_tirage_results(request: HttpRequest, tirage_id: int) -> JsonResponse:
    """
    GET /portal/api/tirages/<id>/results/
    Retourne les résultats actuels d'un tirage pour la session courante.
    
    POST /portal/api/tirages/<id>/results/
    Saisie/mise à jour des résultats.
    """
    guard = _require_admin_api(request)
    if guard:
        return guard

    borlette = get_user_borlette(request.user)
    tirage = Tirage.objects.filter(id=tirage_id, borlette=borlette).first()
    
    if not tirage:
        return JsonResponse({"error": "Tirage non trouvé"}, status=404)

    if request.method == "GET":
        return _get_tirage_results(tirage)
    elif request.method == "POST":
        if not staff_can(request.user, "can_manage_results"):
            return JsonResponse({"error": "Permission refusée"}, status=403)
        return _post_tirage_results(request, tirage)
    
    return JsonResponse({"error": "Méthode non supportée"}, status=405)


def _get_tirage_results(tirage: Tirage) -> JsonResponse:
    """Retourne les résultats actuels du tirage (utilise modèle Resultat existant)."""
    session_key = tirage.session_key
    
    # Chercher résultats existants pour cette session
    result = Resultat.objects.filter(
        tirage=tirage,
        session_key=session_key
    ).first()
    
    # Stats des tickets de cette session
    tickets = Ticket.objects.filter(
        tirage=tirage,
        tirage_session_key=session_key,
        statut=TicketStatus.VALIDE,
    )
    
    tickets_count = tickets.count()
    winners_count = tickets.filter(is_winner=True).count()
    total_mises = tickets.aggregate(t=models.Sum("total_mise"))["t"] or Decimal("0")
    total_gains_du = tickets.aggregate(t=models.Sum("total_gain_du"))["t"] or Decimal("0")
    
    return JsonResponse({
        "tirage": {
            "id": tirage.id,
            "nom": tirage.nom,
            "session_key": str(session_key),
            "etat": tirage.etat_ouverture,
        },
        "results": {
            "lot1": result.lot1 if result else "",
            "lot2": result.lot2 if result else "",
            "lot3": result.lot3 if result else "",
            "chiffre_loto3": result.chiffre_loto3 if result else "",
            "loto3": result.loto3 if result else "",
            "loto4_opt1": result.loto4_opt1 if result else "",
            "loto4_opt2": result.loto4_opt2 if result else "",
            "loto4_opt3": result.loto4_opt3 if result else "",
            "loto5_opt1": result.loto5_opt1 if result else "",
            "loto5_opt2": result.loto5_opt2 if result else "",
            "loto5_opt3": result.loto5_opt3 if result else "",
            "computed_at": result.computed_at.isoformat() if result and result.computed_at else None,
        } if result else None,
        "stats": {
            "tickets_count": tickets_count,
            "winners_count": winners_count,
            "total_mises": float(total_mises),
            "total_gains_du": float(total_gains_du),
        },
        "can_edit": tirage.etat_ouverture == "FERME",
    })


def _post_tirage_results(request: HttpRequest, tirage: Tirage) -> JsonResponse:
    """Saisie des résultats du tirage (utilise modèle Resultat existant)."""
    # Vérifier tirage fermé
    if tirage.etat_ouverture == "OUVERT":
        return JsonResponse({
            "error": "Impossible de saisir les résultats: tirage encore ouvert"
        }, status=400)
    
    try:
        data = json.loads(request.body)
    except json.JSONDecodeError:
        return JsonResponse({"error": "JSON invalide"}, status=400)
    
    # Extraire les lots (3 numéros 00-99 + 1 chiffre 0-9 pour loto3)
    lot1 = data.get("lot1", "").strip().zfill(2) if data.get("lot1") else ""
    lot2 = data.get("lot2", "").strip().zfill(2) if data.get("lot2") else ""
    lot3 = data.get("lot3", "").strip().zfill(2) if data.get("lot3") else ""
    chiffre_loto3 = data.get("chiffre_loto3", "").strip()
    
    # Validation lots (00-99)
    for lot_name, lot_val in [("lot1", lot1), ("lot2", lot2), ("lot3", lot3)]:
        if lot_val and (len(lot_val) != 2 or not lot_val.isdigit()):
            return JsonResponse({"error": f"{lot_name} doit être entre 00 et 99"}, status=400)
    
    # Validation chiffre_loto3 (0-9)
    if chiffre_loto3 and (len(chiffre_loto3) != 1 or not chiffre_loto3.isdigit()):
        return JsonResponse({"error": "chiffre_loto3 doit être un chiffre (0-9)"}, status=400)
    
    # Vérifier que les 3 lots et le chiffre sont fournis
    if not all([lot1, lot2, lot3, chiffre_loto3]):
        return JsonResponse({
            "error": "Les 3 lots et le chiffre loto3 sont requis"
        }, status=400)
    
    session_key = tirage.session_key
    
    with transaction.atomic():
        existing = Resultat.objects.select_for_update().filter(
            tirage=tirage,
            session_key=session_key,
        ).first()
        if existing:
            return JsonResponse(
                {"error": "Résultats déjà saisis pour cette session (override interdit)"},
                status=409,
            )

        result = Resultat.objects.create(
            tirage=tirage,
            session_key=session_key,
            date=timezone.localdate(),
            lot1=lot1,
            lot2=lot2,
            lot3=lot3,
            chiffre_loto3=chiffre_loto3,
            complementaire=chiffre_loto3,
            locked=False,
            computed_at=None,
            source="manual",
            statut="pending",
        )
        created = True
        
        # Lancer calcul des gains via le service existant
        calc_result = ResultCalculationService.calculate_gains(
            tirage=tirage,
            resultat=result,
        )

        log_audit(
            action=AuditAction.RESULTS_SET,
            entity_type="Tirage",
            entity_id=str(tirage.id),
            borlette=get_user_borlette(request.user),
            actor_user=request.user,
            request=request,
            meta={
                "tirage_id": tirage.id,
                "tirage_nom": tirage.nom,
                "session_key": str(session_key),
                "lot1": lot1,
                "lot2": lot2,
                "lot3": lot3,
                "chiffre_loto3": chiffre_loto3,
            },
        )
    
    return JsonResponse({
        "success": True,
        "created": created,
        "results": {
            "lot1": result.lot1,
            "lot2": result.lot2,
            "lot3": result.lot3,
            "chiffre_loto3": result.chiffre_loto3,
            "loto3": result.loto3,
            "loto4_opt1": result.loto4_opt1,
            "loto4_opt2": result.loto4_opt2,
            "loto4_opt3": result.loto4_opt3,
            "loto5_opt1": result.loto5_opt1,
            "loto5_opt2": result.loto5_opt2,
            "loto5_opt3": result.loto5_opt3,
        },
        "calculation": calc_result,
    })


@login_required
@require_POST
@transaction.atomic
def api_resultat_validate(request: HttpRequest, resultat_id: int) -> JsonResponse:
    guard = _require_admin_api(request)
    if guard:
        return guard

    if not staff_can(request.user, "can_manage_results"):
        return JsonResponse({"error": "Permission refusée"}, status=403)

    borlette = get_user_borlette(request.user)
    r = (
        Resultat.objects.select_for_update()
        .select_related("tirage")
        .filter(id=resultat_id, tirage__borlette=borlette)
        .first()
    )
    if not r:
        return JsonResponse({"error": "Résultat introuvable"}, status=404)

    now = timezone.now()
    r.statut = "validated"
    r.validated_by = request.user
    r.validated_at = now
    r.rejected_by = None
    r.rejected_at = None
    r.save(update_fields=["statut", "validated_by", "validated_at", "rejected_by", "rejected_at"])

    # Lancer le calcul des gains au moment de la validation
    calc_result = ResultCalculationService.calculate_gains(
        tirage=r.tirage,
        resultat=r,
    )

    log_audit(
        action=AuditAction.RESULTS_VALIDATE,
        entity_type="Resultat",
        entity_id=str(r.id),
        borlette=borlette,
        actor_user=request.user,
        request=request,
        meta={
            "resultat_id": r.id,
            "tirage_id": r.tirage_id,
            "tirage_nom": r.tirage.nom,
            "date": r.date.isoformat(),
            "statut": r.statut,
        },
    )

    return JsonResponse({"success": True, "id": r.id, "statut": r.statut})


@login_required
@require_POST
@transaction.atomic
def api_resultat_reject(request: HttpRequest, resultat_id: int) -> JsonResponse:
    guard = _require_admin_api(request)
    if guard:
        return guard

    if not staff_can(request.user, "can_manage_results"):
        return JsonResponse({"error": "Permission refusée"}, status=403)

    borlette = get_user_borlette(request.user)
    r = (
        Resultat.objects.select_for_update()
        .select_related("tirage")
        .filter(id=resultat_id, tirage__borlette=borlette)
        .first()
    )
    if not r:
        return JsonResponse({"error": "Résultat introuvable"}, status=404)

    now = timezone.now()
    r.statut = "rejected"
    r.rejected_by = request.user
    r.rejected_at = now
    r.validated_by = None
    r.validated_at = None
    r.save(update_fields=["statut", "rejected_by", "rejected_at", "validated_by", "validated_at"])

    log_audit(
        action=AuditAction.RESULTS_REJECT,
        entity_type="Resultat",
        entity_id=str(r.id),
        borlette=borlette,
        actor_user=request.user,
        request=request,
        meta={
            "resultat_id": r.id,
            "tirage_id": r.tirage_id,
            "tirage_nom": r.tirage.nom,
            "date": r.date.isoformat(),
            "statut": r.statut,
        },
    )

    return JsonResponse({"success": True, "id": r.id, "statut": r.statut})


@login_required
@require_POST
@transaction.atomic
def api_resultat_pending(request: HttpRequest, resultat_id: int) -> JsonResponse:
    guard = _require_admin_api(request)
    if guard:
        return guard

    if not staff_can(request.user, "can_manage_results"):
        return JsonResponse({"error": "Permission refusée"}, status=403)

    borlette = get_user_borlette(request.user)
    r = (
        Resultat.objects.select_for_update()
        .select_related("tirage")
        .filter(id=resultat_id, tirage__borlette=borlette)
        .first()
    )
    if not r:
        return JsonResponse({"error": "Résultat introuvable"}, status=404)

    r.statut = "pending"
    r.validated_by = None
    r.validated_at = None
    r.rejected_by = None
    r.rejected_at = None
    r.save(update_fields=["statut", "validated_by", "validated_at", "rejected_by", "rejected_at"])

    log_audit(
        action=AuditAction.RESULTS_PENDING,
        entity_type="Resultat",
        entity_id=str(r.id),
        borlette=borlette,
        actor_user=request.user,
        request=request,
        meta={
            "resultat_id": r.id,
            "tirage_id": r.tirage_id,
            "tirage_nom": r.tirage.nom,
            "date": r.date.isoformat(),
            "statut": r.statut,
        },
    )

    return JsonResponse({"success": True, "id": r.id, "statut": r.statut})


@login_required
@require_POST
@transaction.atomic
def api_resultat_flag(request: HttpRequest, resultat_id: int) -> JsonResponse:
    guard = _require_admin_api(request)
    if guard:
        return guard

    if not staff_can(request.user, "can_manage_results"):
        return JsonResponse({"error": "Permission refusée"}, status=403)

    borlette = get_user_borlette(request.user)
    r = (
        Resultat.objects.select_for_update()
        .select_related("tirage")
        .filter(id=resultat_id, tirage__borlette=borlette)
        .first()
    )
    if not r:
        return JsonResponse({"error": "Résultat introuvable"}, status=404)

    r.is_suspicious = True
    r.save(update_fields=["is_suspicious"])

    log_audit(
        action=AuditAction.RESULTS_FLAG,
        entity_type="Resultat",
        entity_id=str(r.id),
        borlette=borlette,
        actor_user=request.user,
        request=request,
        meta={
            "resultat_id": r.id,
            "tirage_id": r.tirage_id,
            "tirage_nom": r.tirage.nom,
            "date": r.date.isoformat(),
            "is_suspicious": True,
        },
    )

    return JsonResponse({"success": True, "id": r.id, "is_suspicious": True})


@login_required
def api_ticket_search(request: HttpRequest) -> JsonResponse:
    """
    GET /portal/api/tickets/search/?q=...
    Recherche un ticket par numéro ou UUID.
    """
    guard = _require_admin_api(request)
    if guard:
        return guard

    borlette = get_user_borlette(request.user)
    query = request.GET.get("q", "").strip()
    
    if not query:
        return JsonResponse({"error": "Paramètre 'q' requis"}, status=400)
    
    # Chercher par numéro ou UUID
    ticket = Ticket.objects.filter(
        borlette=borlette
    ).filter(
        models.Q(numero_ticket__icontains=query) | models.Q(id__icontains=query)
    ).select_related("agent", "tirage").prefetch_related("lignes").first()
    
    if not ticket:
        return JsonResponse({"error": "Ticket non trouvé"}, status=404)
    
    # Calculer si suppression possible (< 2 min ou tirage ouvert, pas payé)
    now = timezone.now()
    is_draw_open = (ticket.tirage and ticket.tirage.etat_ouverture == "OUVERT") if ticket.tirage else True
    can_delete = (
        ((now - ticket.created_at) <= timedelta(minutes=2) or is_draw_open)
        and ticket.total_gain_paye == 0
        and ticket.statut != TicketStatus.ANNULE
    )
    
    # Lignes du ticket
    lines = []
    for line in ticket.lignes.all():
        lines.append({
            "jeu": line.jeu,
            "valeur": line.valeur,
            "mise": float(line.mise),
            "potentiel_gain": float(line.potentiel_gain),
            "gain_du": float(line.gain_du),
            "is_winner": line.is_winner,
            "win_context": line.win_context,
            "gratuit": line.gratuit,
        })
    
    return JsonResponse({
        "ticket": {
            "id": str(ticket.id),
            "numero": ticket.numero_ticket,
            "agent": {
                "id": ticket.agent.id,
                "nom": ticket.agent.nom,
            },
            "tirage": {
                "id": ticket.tirage.id if ticket.tirage else None,
                "nom": ticket.tirage.nom if ticket.tirage else "N/A",
            },
            "statut": ticket.statut,
            "total_mise": float(ticket.total_mise),
            "total_gain_du": float(ticket.total_gain_du),
            "total_gain_paye": float(ticket.total_gain_paye),
            "reste_a_payer": float(ticket.total_gain_du - ticket.total_gain_paye),
            "is_winner": ticket.is_winner,
            "is_paid": ticket.is_paid,
            "computed_at": ticket.computed_at.isoformat() if ticket.computed_at else None,
            "created_at": ticket.created_at.isoformat(),
            "lines": lines,
        },
        "actions": {
            "can_print": True,
            "can_refaire": True,
            "can_pay": ticket.is_winner and not ticket.is_paid,
            "can_delete": can_delete,
        },
    })


@login_required
@require_POST
@transaction.atomic
def api_ticket_void(request: HttpRequest, ticket_id: str) -> JsonResponse:
    """
    POST /portal/api/tickets/<uuid>/void/
    Annule un ticket (si < 7 minutes et pas payé).
    Crée écriture inverse caisse si nécessaire.
    """
    from agent_portal.services import void_ticket_with_cashbox_reversal
    
    guard = _require_admin_api(request)
    if guard:
        return guard

    borlette = get_user_borlette(request.user)
    
    try:
        ticket = Ticket.objects.select_for_update().get(id=ticket_id, borlette=borlette)
    except Ticket.DoesNotExist:
        return JsonResponse({"error": "Ticket non trouvé"}, status=404)
    
    # Annulation possible si age <= 2 min OU tirage ouvert
    now = timezone.now()
    age = now - ticket.created_at
    is_draw_open = (ticket.tirage and ticket.tirage.etat_ouverture == "OUVERT") if ticket.tirage else True
    
    can_void = (age <= timedelta(minutes=2) or is_draw_open) and ticket.total_gain_paye == 0 and ticket.statut != TicketStatus.ANNULE
    
    if not can_void:
        return JsonResponse({
            "error": "Annulation impossible: le délai de 2 minutes est dépassé et le tirage est fermé."
        }, status=400)
    
    # Utiliser le service qui gère l'écriture inverse caisse
    result = void_ticket_with_cashbox_reversal(ticket)
    
    if not result["success"]:
        return JsonResponse({"error": result["error"]}, status=400)

    log_audit(
        action=AuditAction.TICKET_VOID,
        entity_type="Ticket",
        entity_id=str(ticket.id),
        borlette=borlette,
        actor_user=request.user,
        request=request,
        meta={
            "ticket_no": ticket.numero_ticket,
            "total_mise": float(ticket.total_mise),
            "reason": "time_window",
            "cashbox_reversed": bool(result.get("cashbox_reversed")),
        },
    )
    
    return JsonResponse({
        "success": True,
        "message": result["message"],
        "cashbox_reversed": result["cashbox_reversed"],
    })


@login_required
@require_POST
@transaction.atomic
def api_ticket_pay_admin(request: HttpRequest, ticket_id: str) -> JsonResponse:
    """
    POST /portal/api/tickets/<uuid>/pay/
    Paye un ticket gagnant (TOTAL uniquement, côté admin).
    """
    guard = _require_admin_api(request)
    if guard:
        return guard

    borlette = get_user_borlette(request.user)
    
    try:
        ticket = Ticket.objects.select_for_update().select_related("agent").get(id=ticket_id, borlette=borlette)
    except Ticket.DoesNotExist:
        return JsonResponse({"error": "Ticket non trouvé"}, status=404)
    
    try:
        data = json.loads(request.body)
    except json.JSONDecodeError:
        data = {}
    
    # Paiement TOTAL uniquement - on ignore tout paramètre amount
    note = data.get("note", "Paiement via admin portal")

    from agent_portal.models import AgentCashboxEntry
    cashbox_before = AgentCashboxEntry.get_agent_cashbox_balance(ticket.agent).get("balance")
    
    # Utiliser le service de paiement (TOTAL uniquement)
    result = TicketPayoutService.pay_ticket(
        ticket=ticket,
        agent=ticket.agent,
        note=note,
    )
    
    if not result["success"]:
        return JsonResponse({"error": result["error"]}, status=400)

    cashbox_after = AgentCashboxEntry.get_agent_cashbox_balance(ticket.agent).get("balance")

    log_audit(
        action=AuditAction.TICKET_PAYOUT,
        entity_type="Ticket",
        entity_id=str(ticket.id),
        borlette=borlette,
        actor_user=request.user,
        request=request,
        meta={
            "ticket_no": ticket.numero_ticket,
            "agent_id": ticket.agent.id,
            "agent_nom": ticket.agent.nom,
            "payout_id": result.get("payout_id"),
            "amount_paid": float(result.get("amount_paid") or 0),
            "note": note,
            "cashbox_before": float(cashbox_before) if cashbox_before is not None else None,
            "cashbox_after": float(cashbox_after) if cashbox_after is not None else None,
        },
    )
    
    return JsonResponse(result)


@login_required
def api_winners_by_tirage(request: HttpRequest, tirage_id: int) -> JsonResponse:
    """
    GET /portal/api/tirages/<id>/winners/
    Liste des tickets gagnants pour un tirage/session.
    """
    guard = _require_admin_api(request)
    if guard:
        return guard

    borlette = get_user_borlette(request.user)
    tirage = Tirage.objects.filter(id=tirage_id, borlette=borlette).first()
    
    if not tirage:
        return JsonResponse({"error": "Tirage non trouvé"}, status=404)
    
    session_key = tirage.session_key
    
    tickets = Ticket.objects.filter(
        tirage=tirage,
        tirage_session_key=session_key,
        is_winner=True,
        statut=TicketStatus.VALIDE,
    ).select_related("agent").order_by("-total_gain_du")[:100]
    
    data = []
    for t in tickets:
        data.append({
            "id": str(t.id),
            "numero": t.numero_ticket,
            "agent": t.agent.nom,
            "total_mise": float(t.total_mise),
            "total_gain_du": float(t.total_gain_du),
            "total_gain_paye": float(t.total_gain_paye),
            "is_paid": t.is_paid,
            "created_at": t.created_at.isoformat(),
        })
    
    return JsonResponse({
        "tirage": tirage.nom,
        "session_key": str(session_key),
        "winners": data,
        "total_count": len(data),
    })


@login_required
def api_tirages_results_status(request: HttpRequest) -> JsonResponse:
    """
    GET /portal/api/tirages/results_status/
    Retourne le statut des résultats pour tous les tirages.
    """
    guard = _require_admin_api(request)
    if guard:
        return guard
    
    from .tirage_results_service import TirageResultsStatusService
    
    service = TirageResultsStatusService(get_user_borlette(request.user))
    all_statuses = service.get_all_statuses()
    
    return JsonResponse({
        "success": True,
        "tirages": [service.to_dict(s) for s in all_statuses],
        "pending_count": len([s for s in all_statuses if not s.is_open and not s.has_results]),
        "overdue_count": len([s for s in all_statuses if s.is_overdue]),
    })


@login_required
def api_ticket_pdf_admin(request: HttpRequest, ticket_id: str) -> HttpResponse:
    """
    GET /portal/api/tickets/<uuid>/pdf/
    Génère un PDF du ticket pour l'admin ou le superadmin.
    """
    from django.http import HttpResponse
    
    # Check role
    if request.user.role not in (UserRole.ADMIN, UserRole.SUPER_ADMIN):
        return HttpResponse("Accès refusé", status=403)

    borlette = get_user_borlette(request.user) if request.user.role != UserRole.SUPER_ADMIN else None
    
    # Query ticket
    q = Ticket.objects.select_related("tirage", "agent", "borlette").prefetch_related("lignes")
    if borlette:
        q = q.filter(borlette=borlette)
    
    ticket = q.filter(id=ticket_id).first()
    if not ticket:
        return HttpResponse("Ticket non trouvé", status=404)

    borlette_obj = ticket.borlette
    created = timezone.localtime(ticket.created_at)

    from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle
    from reportlab.lib.styles import getSampleStyleSheet
    from reportlab.lib import colors
    from reportlab.lib.units import mm

    # Créer le PDF
    response = HttpResponse(content_type='application/pdf')
    response['Content-Disposition'] = f'inline; filename="ticket_{ticket.numero_ticket}.pdf"'

    # Configuration du document (80mm de largeur comme un ticket thermique)
    doc = SimpleDocTemplate(
        response,
        pagesize=(80 * mm, 200 * mm),
        rightMargin=5 * mm,
        leftMargin=5 * mm,
        topMargin=5 * mm,
        bottomMargin=5 * mm,
    )

    elements = []
    styles = getSampleStyleSheet()

    # En-tête
    elements.append(Paragraph(f"<b>{borlette_obj.nom_borlette}</b>", styles['Center']))
    elements.append(Paragraph(borlette_obj.slogan or "", styles['Center']))
    elements.append(Spacer(1, 2 * mm))
    elements.append(Paragraph(f"Tel: {borlette_obj.telephone or ''}", styles['Normal']))
    elements.append(Paragraph(borlette_obj.ville or "", styles['Normal']))
    elements.append(Spacer(1, 3 * mm))

    # Ligne de séparation
    elements.append(Paragraph("-" * 30, styles['Center']))
    elements.append(Spacer(1, 2 * mm))

    # Info ticket
    elements.append(Paragraph(f"<b>Ticket:</b> {ticket.numero_ticket}", styles['Normal']))
    elements.append(Paragraph(f"<b>Date:</b> {created.strftime('%d/%m/%Y')}", styles['Normal']))
    elements.append(Paragraph(f"<b>Heure:</b> {created.strftime('%H:%M')}", styles['Normal']))
    elements.append(Spacer(1, 2 * mm))
    elements.append(Paragraph(f"<b>Agent:</b> {ticket.agent.nom}", styles['Normal']))
    elements.append(Paragraph(f"<b>Tirage:</b> {ticket.tirage.nom if ticket.tirage else '—'}", styles['Normal']))
    elements.append(Spacer(1, 3 * mm))

    # Ligne de séparation
    elements.append(Paragraph("-" * 30, styles['Center']))
    elements.append(Spacer(1, 2 * mm))

    # Lignes du ticket
    table_data = [['JEU', 'VALEUR', 'MISE']]
    for l in ticket.lignes.all():
        jeu = l.jeu.upper()
        valeur = l.valeur
        mise = str(l.mise) if not l.gratuit else "GRATUIT"
        table_data.append([jeu, valeur, mise])

    table = Table(table_data, colWidths=[20 * mm, 20 * mm, 20 * mm])
    table.setStyle(TableStyle([
        ('BACKGROUND', (0, 0), (-1, 0), colors.grey),
        ('TEXTCOLOR', (0, 0), (-1, 0), colors.whitesmoke),
        ('ALIGN', (0, 0), (-1, -1), 'CENTER'),
        ('FONTNAME', (0, 0), (-1, 0), 'Helvetica-Bold'),
        ('FONTSIZE', (0, 0), (-1, 0), 9),
        ('BOTTOMPADDING', (0, 0), (-1, 0), 4),
        ('BACKGROUND', (0, 1), (-1, -1), colors.beige),
        ('GRID', (0, 0), (-1, -1), 0.5, colors.black),
    ]))
    elements.append(table)
    elements.append(Spacer(1, 3 * mm))

    # Total
    elements.append(Paragraph(f"<b>Total:</b> {ticket.total_mise} HTG", styles['Normal']))
    elements.append(Spacer(1, 3 * mm))

    # Signature
    elements.append(Paragraph(f"<b>Statut:</b> {ticket.statut}", styles['Normal']))
    if ticket.statut == TicketStatus.ANNULE:
        elements.append(Paragraph("<font color='red'><b>TICKET ANNULE</b></font>", styles['Center']))

    doc.build(elements)
    return response
