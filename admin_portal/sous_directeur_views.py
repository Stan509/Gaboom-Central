from decimal import Decimal
from django.contrib import messages
from django.contrib.auth import get_user_model
from django.contrib.auth.decorators import login_required
from django.db import transaction
from django.db.models import Sum, Count
from django.http import HttpRequest, JsonResponse
from django.shortcuts import get_object_or_404, redirect, render
from django.utils import timezone

from accounts.models import (
    Agent,
    AgentStatus,
    Borlette,
    SousDirecteur,
    SousDirecteurTiragePreference,
    Tirage,
    TirageStatus,
    UserRole,
)
from admin_portal.security import get_user_borlette, get_sous_directeur

User = get_user_model()


def _require_admin_or_manager(request):
    if request.user.role != UserRole.ADMIN:
        return redirect("/portal/dashboard/")
    borlette = get_user_borlette(request.user)
    if not borlette:
        return redirect("/portal/dashboard/")
    return None


def _require_sous_directeur(request):
    if request.user.role != UserRole.SOUS_DIRECTEUR:
        return redirect("/portal/dashboard/")
    sd = get_sous_directeur(request.user)
    if not sd:
        messages.error(request, "Profil sous-directeur introuvable ou inactif.")
        return redirect("/portal/login/")
    return None


# ═══════════════════════════════════════════════════════════════════════════
# VUES POUR L'ADMINISTRATEUR (DIRECTEUR BORLETTE)
# ═══════════════════════════════════════════════════════════════════════════

@login_required
def sous_directeurs_list(request: HttpRequest):
    """Gestion des sous-directeurs par le directeur borlette."""
    guard = _require_admin_or_manager(request)
    if guard:
        return guard

    borlette = get_user_borlette(request.user)
    sous_directeurs = SousDirecteur.objects.filter(borlette=borlette).annotate(
        agents_count=Count("agents")
    ).select_related("user").order_by("-created_at")

    unassigned_agents = Agent.objects.filter(borlette=borlette, sous_directeur__isnull=True).order_by("nom")

    return render(
        request,
        "admin_portal/sous_directeurs_list.html",
        {
            "borlette": borlette,
            "sous_directeurs": sous_directeurs,
            "unassigned_agents": unassigned_agents,
        },
    )


@login_required
def sous_directeur_create(request: HttpRequest):
    """Création d'un nouveau sous-directeur."""
    guard = _require_admin_or_manager(request)
    if guard:
        return guard

    borlette = get_user_borlette(request.user)

    if request.method == "POST":
        username = (request.POST.get("username") or "").strip()
        password = (request.POST.get("password") or "").strip()
        nom = (request.POST.get("nom") or "").strip()
        telephone = (request.POST.get("telephone") or "").strip()
        zone = (request.POST.get("zone") or "").strip()
        commission_percent_str = (request.POST.get("commission_percent") or "14.00").strip()

        if not username or not password or not nom:
            messages.error(request, "Le nom d'utilisateur, le mot de passe et le nom complet sont requis.")
            return redirect("admin_portal:sous_directeurs_list")

        if User.objects.filter(username=username).exists():
            messages.error(request, f"Le nom d'utilisateur '{username}' existe déjà.")
            return redirect("admin_portal:sous_directeurs_list")

        try:
            commission_percent = Decimal(commission_percent_str)
            if commission_percent < 0 or commission_percent > 100:
                raise ValueError()
        except Exception:
            messages.error(request, "Le pourcentage de commission doit être compris entre 0 et 100.")
            return redirect("admin_portal:sous_directeurs_list")

        with transaction.atomic():
            user = User.objects.create_user(
                username=username,
                password=password,
                role=UserRole.SOUS_DIRECTEUR,
            )
            SousDirecteur.objects.create(
                user=user,
                borlette=borlette,
                nom=nom,
                telephone=telephone,
                zone=zone,
                commission_percent=commission_percent,
                is_active=True,
            )

        messages.success(request, f"Le sous-directeur '{nom}' ({commission_percent}%) a été créé avec succès.")
        return redirect("admin_portal:sous_directeurs_list")

    return redirect("admin_portal:sous_directeurs_list")


@login_required
def sous_directeur_edit(request: HttpRequest, sd_id: int):
    """Modification d'un sous-directeur existant."""
    guard = _require_admin_or_manager(request)
    if guard:
        return guard

    borlette = get_user_borlette(request.user)
    sd = get_object_or_404(SousDirecteur, id=sd_id, borlette=borlette)

    if request.method == "POST":
        sd.nom = (request.POST.get("nom") or sd.nom).strip()
        sd.telephone = (request.POST.get("telephone") or "").strip()
        sd.zone = (request.POST.get("zone") or "").strip()
        sd.is_active = (request.POST.get("is_active") or "") == "on"

        commission_percent_str = (request.POST.get("commission_percent") or "").strip()
        if commission_percent_str:
            try:
                comm = Decimal(commission_percent_str)
                if 0 <= comm <= 100:
                    sd.commission_percent = comm
            except Exception:
                pass

        password = (request.POST.get("password") or "").strip()
        if password:
            sd.user.set_password(password)
            sd.user.save(update_fields=["password"])

        sd.save()
        messages.success(request, f"Sous-directeur '{sd.nom}' mis à jour.")
        return redirect("admin_portal:sous_directeurs_list")

    return redirect("admin_portal:sous_directeurs_list")


@login_required
def sous_directeur_delete(request: HttpRequest, sd_id: int):
    """Suppression d'un sous-directeur."""
    guard = _require_admin_or_manager(request)
    if guard:
        return guard

    borlette = get_user_borlette(request.user)
    sd = get_object_or_404(SousDirecteur, id=sd_id, borlette=borlette)

    if request.method == "POST":
        nom = sd.nom
        user = sd.user
        with transaction.atomic():
            # Détacher les agents du sous-directeur (ils restent dans la borlette)
            Agent.objects.filter(sous_directeur=sd).update(sous_directeur=None)
            sd.delete()
            if user:
                user.delete()

        messages.success(request, f"Le sous-directeur '{nom}' a été supprimé. Ses agents ont été conservés.")
        return redirect("admin_portal:sous_directeurs_list")

    return redirect("admin_portal:sous_directeurs_list")


@login_required
def sous_directeur_assign_agent(request: HttpRequest, sd_id: int):
    """Assigne un agent de la borlette à un sous-directeur."""
    guard = _require_admin_or_manager(request)
    if guard:
        return guard

    borlette = get_user_borlette(request.user)
    sd = get_object_or_404(SousDirecteur, id=sd_id, borlette=borlette)

    if request.method == "POST":
        agent_id = request.POST.get("agent_id")
        action = request.POST.get("action", "assign")

        if action == "unassign":
            agent = get_object_or_404(Agent, id=agent_id, borlette=borlette, sous_directeur=sd)
            agent.sous_directeur = None
            agent.save(update_fields=["sous_directeur"])
            messages.success(request, f"L'agent '{agent.nom}' a été retiré de la juridiction de {sd.nom}.")
        else:
            agent = get_object_or_404(Agent, id=agent_id, borlette=borlette)
            # S'assurer que la commission de l'agent ne dépasse pas celle du sous-directeur
            if agent.commission > sd.commission_percent:
                agent.commission = sd.commission_percent
                agent.save(update_fields=["commission"])
            agent.sous_directeur = sd
            agent.save(update_fields=["sous_directeur"])
            messages.success(request, f"L'agent '{agent.nom}' est maintenant rattaché à {sd.nom}.")

        return redirect("admin_portal:sous_directeurs_list")

    return redirect("admin_portal:sous_directeurs_list")


# ═══════════════════════════════════════════════════════════════════════════
# VUES DÉDIÉES À L'ESPACE SOUS-DIRECTEUR (JURIDICTION STRICTE)
# ═══════════════════════════════════════════════════════════════════════════

@login_required
def sous_directeur_dashboard(request: HttpRequest):
    """Tableau de bord restreint du sous-directeur."""
    guard = _require_sous_directeur(request)
    if guard:
        return guard

    sd = get_sous_directeur(request.user)
    borlette = sd.borlette

    from agent_portal.models import Ticket, TicketStatus

    agents = Agent.objects.filter(sous_directeur=sd).select_related("user").order_by("nom")
    total_agents = agents.count()
    online_agents = sum(1 for a in agents if a.is_online)

    total_mises = Ticket.objects.filter(
        agent__in=agents,
        statut=TicketStatus.VALIDE
    ).aggregate(total=Sum("total_mise"))["total"] or Decimal("0.00")

    # Calcul de la commission du sous-directeur
    # Pour chaque agent: ventes * (sd.commission_percent - agent.commission) / 100
    sous_dir_commission = Decimal("0.00")
    total_agent_commissions = Decimal("0.00")

    for a in agents:
        agent_sales = Ticket.objects.filter(
            agent=a,
            statut=TicketStatus.VALIDE
        ).aggregate(total=Sum("total_mise"))["total"] or Decimal("0.00")
        
        a.sales = agent_sales
        # Part agent
        agent_part = agent_sales * (a.commission / Decimal("100.0"))
        total_agent_commissions += agent_part
        
        # Marge sous-directeur (ex: 14% - 10% = 4%)
        margin_percent = max(Decimal("0.00"), sd.commission_percent - a.commission)
        a.margin_percent = margin_percent
        a.sd_profit = agent_sales * (margin_percent / Decimal("100.0"))
        sous_dir_commission += a.sd_profit

    return render(
        request,
        "admin_portal/sous_directeur_dashboard.html",
        {
            "sd": sd,
            "borlette": borlette,
            "agents": agents,
            "total_agents": total_agents,
            "online_agents": online_agents,
            "total_mises": total_mises,
            "total_agent_commissions": total_agent_commissions,
            "sous_dir_commission": sous_dir_commission,
        },
    )


@login_required
def sous_directeur_agents(request: HttpRequest):
    """Liste et gestion des agents sous la juridiction du sous-directeur."""
    guard = _require_sous_directeur(request)
    if guard:
        return guard

    sd = get_sous_directeur(request.user)
    agents = Agent.objects.filter(sous_directeur=sd).select_related("user").order_by("-date_creation")

    return render(
        request,
        "admin_portal/sous_directeur_agents.html",
        {
            "sd": sd,
            "agents": agents,
        },
    )


@login_required
def sous_directeur_agent_create(request: HttpRequest):
    """Création d'un agent sous la juridiction du sous-directeur."""
    guard = _require_sous_directeur(request)
    if guard:
        return guard

    sd = get_sous_directeur(request.user)

    if request.method == "POST":
        username = (request.POST.get("username") or "").strip()
        password = (request.POST.get("password") or "").strip()
        nom = (request.POST.get("nom") or "").strip()
        telephone = (request.POST.get("telephone") or "").strip()
        zone = (request.POST.get("zone") or sd.zone or "").strip()
        commission_str = (request.POST.get("commission") or "10.00").strip()

        if not username or not password or not nom:
            messages.error(request, "Veuillez remplir tous les champs obligatoires.")
            return redirect("admin_portal:sous_directeur_agents")

        if User.objects.filter(username=username).exists():
            messages.error(request, f"Le nom d'utilisateur '{username}' est déjà pris.")
            return redirect("admin_portal:sous_directeur_agents")

        try:
            commission = Decimal(commission_str)
            if commission < 0 or commission > sd.commission_percent:
                messages.error(
                    request,
                    f"La commission de l'agent ({commission}%) ne peut pas dépasser votre taux ({sd.commission_percent}%)."
                )
                return redirect("admin_portal:sous_directeur_agents")
        except Exception:
            messages.error(request, "Commission invalide.")
            return redirect("admin_portal:sous_directeur_agents")

        with transaction.atomic():
            user = User.objects.create_user(
                username=username,
                password=password,
                role=UserRole.AGENT,
            )
            Agent.objects.create(
                user=user,
                borlette=sd.borlette,
                sous_directeur=sd,
                nom=nom,
                telephone=telephone,
                zone=zone,
                commission=commission,
                statut=AgentStatus.ACTIF,
            )

        messages.success(request, f"L'agent '{nom}' a été créé avec succès sous votre juridiction.")
        return redirect("admin_portal:sous_directeur_agents")

    return redirect("admin_portal:sous_directeur_agents")


@login_required
def sous_directeur_agent_edit(request: HttpRequest, agent_id: int):
    """Modification d'un agent sous la juridiction du sous-directeur."""
    guard = _require_sous_directeur(request)
    if guard:
        return guard

    sd = get_sous_directeur(request.user)
    agent = get_object_or_404(Agent, id=agent_id, sous_directeur=sd)

    if request.method == "POST":
        agent.nom = (request.POST.get("nom") or agent.nom).strip()
        agent.telephone = (request.POST.get("telephone") or "").strip()
        agent.zone = (request.POST.get("zone") or "").strip()

        commission_str = (request.POST.get("commission") or "").strip()
        if commission_str:
            try:
                comm = Decimal(commission_str)
                if 0 <= comm <= sd.commission_percent:
                    agent.commission = comm
                else:
                    messages.warning(request, f"La commission doit être <= {sd.commission_percent}%.")
            except Exception:
                pass

        password = (request.POST.get("password") or "").strip()
        if password:
            agent.user.set_password(password)
            agent.user.save(update_fields=["password"])

        agent.save()
        messages.success(request, f"Agent '{agent.nom}' mis à jour.")
        return redirect("admin_portal:sous_directeur_agents")

    return redirect("admin_portal:sous_directeur_agents")


@login_required
def sous_directeur_agent_toggle(request: HttpRequest, agent_id: int):
    """Active ou suspend un agent sous la juridiction du sous-directeur."""
    guard = _require_sous_directeur(request)
    if guard:
        return guard

    sd = get_sous_directeur(request.user)
    agent = get_object_or_404(Agent, id=agent_id, sous_directeur=sd)

    if request.method == "POST":
        if agent.statut == AgentStatus.ACTIF:
            agent.statut = AgentStatus.SUSPENDU
            agent.user.is_active = False
            msg = f"L'agent '{agent.nom}' a été suspendu."
        else:
            agent.statut = AgentStatus.ACTIF
            agent.user.is_active = True
            msg = f"L'agent '{agent.nom}' a été réactivé."

        agent.save(update_fields=["statut"])
        agent.user.save(update_fields=["is_active"])
        messages.success(request, msg)

    return redirect("admin_portal:sous_directeur_agents")


@login_required
def sous_directeur_tirages(request: HttpRequest):
    """Contrôle des tirages pour la juridiction du sous-directeur."""
    guard = _require_sous_directeur(request)
    if guard:
        return guard

    sd = get_sous_directeur(request.user)
    borlette = sd.borlette

    # Tirages vendus par l'administrateur de la borlette
    tirages = Tirage.objects.filter(
        borlette=borlette,
        statut=TirageStatus.ACTIF
    ).order_by("heure_fermeture")

    # Préférences locales du sous-directeur
    prefs = {
        p.tirage_id: p.actif
        for p in SousDirecteurTiragePreference.objects.filter(sous_directeur=sd)
    }

    tirages_list = []
    for t in tirages:
        # Par défaut actif sauf si explicitement désactivé par le sous-directeur
        t.is_active_for_sd = prefs.get(t.id, True)
        tirages_list.append(t)

    return render(
        request,
        "admin_portal/sous_directeur_tirages.html",
        {
            "sd": sd,
            "tirages": tirages_list,
        },
    )


@login_required
def sous_directeur_tirage_toggle(request: HttpRequest, tirage_id: int):
    """Active ou désactive la vente d'un tirage pour la juridiction du sous-directeur."""
    guard = _require_sous_directeur(request)
    if guard:
        return guard

    sd = get_sous_directeur(request.user)
    borlette = sd.borlette
    tirage = get_object_or_404(Tirage, id=tirage_id, borlette=borlette)

    if request.method == "POST":
        pref, _ = SousDirecteurTiragePreference.objects.get_or_create(
            sous_directeur=sd,
            tirage=tirage,
            defaults={"actif": True}
        )
        pref.actif = not pref.actif
        pref.save(update_fields=["actif"])

        status_str = "activé" if pref.actif else "désactivé pour vos agents"
        messages.success(request, f"Le tirage '{tirage.nom}' a été {status_str}.")

    return redirect("admin_portal:sous_directeur_tirages")
