"""
ResultCalculationService: Calcul instantané et idempotent des gains sur tickets.

Déclenché quand admin enregistre les résultats d'un tirage fermé.
Recalcule gain_du, is_winner, win_context sur chaque TicketLine de la session.
"""
from __future__ import annotations

from decimal import Decimal
from typing import TYPE_CHECKING

from django.db import transaction
from django.utils import timezone

if TYPE_CHECKING:
    from accounts.models import Resultat, Tirage


class ResultCalculationService:
    """Service de calcul des gains sur tickets après saisie des résultats."""

    @staticmethod
    @transaction.atomic
    def calculate_gains(*, tirage: Tirage, resultat: Resultat) -> dict:
        """
        Calcule les gains pour tous les tickets de la session courante du tirage.
        
        Args:
            tirage: Le tirage concerné
            resultat: Le résultat saisi (doit avoir session_key == tirage.session_key)
        
        Returns:
            dict avec stats: tickets_count, winners_count, total_gain_du
        """
        from accounts.models import AdminPaymentSettings
        from agent_portal.models import Ticket, TicketLine, TicketStatus

        # Vérifications
        if tirage.etat_ouverture == "OUVERT":
            raise ValueError(f"Impossible de calculer les gains: le tirage '{tirage.nom}' est encore ouvert")

        # Récupérer les coefficients de paiement
        try:
            settings = AdminPaymentSettings.objects.get(borlette=tirage.borlette)
        except AdminPaymentSettings.DoesNotExist:
            settings = None

        coeff_1er = Decimal(str(getattr(settings, "boule_1er_lot_coeff", 0) or 0))
        coeff_2eme = Decimal(str(getattr(settings, "boule_2eme_lot_coeff", 0) or 0))
        coeff_3eme = Decimal(str(getattr(settings, "boule_3eme_lot_coeff", 0) or 0))
        coeff_loto3 = Decimal(str(getattr(settings, "loto3_coeff", 0) or 0))
        coeff_loto4 = Decimal(str(getattr(settings, "loto4_coeff", 0) or 0))
        coeff_loto5 = Decimal(str(getattr(settings, "loto5_coeff", 0) or 0))
        coeff_mariage = Decimal(str(getattr(settings, "mariage_normal_coeff", 0) or 0))
        payout_mariage_gratuit = Decimal(str(getattr(settings, "mariage_gratuit_montant_fixe", 0) or 0))

        # Extraire les numéros gagnants du résultat
        lot1 = (resultat.lot1 or "").strip()
        lot2 = (resultat.lot2 or "").strip()
        lot3 = (resultat.lot3 or "").strip()
        
        loto3_val = resultat.loto3
        loto4_vals = [resultat.loto4_opt1, resultat.loto4_opt2, resultat.loto4_opt3]
        loto5_vals = [resultat.loto5_opt1, resultat.loto5_opt2, resultat.loto5_opt3]

        import datetime
        from django.db.models import Q

        # Sélectionner UNIQUEMENT les tickets appartenant à ce résultat :
        # 1. Tickets portant la même session_key que le résultat
        # 2. Tickets de ce tirage créés le même jour local que le résultat (tickets hors ligne synchronisés)
        tz = timezone.get_current_timezone()
        dt_start = timezone.make_aware(datetime.datetime.combine(resultat.date, datetime.time.min), tz)
        dt_end = timezone.make_aware(datetime.datetime.combine(resultat.date, datetime.time.max), tz)

        tickets = Ticket.objects.filter(
            tirage=tirage,
            statut=TicketStatus.VALIDE,
        ).filter(
            Q(tirage_session_key=resultat.session_key) |
            (Q(created_at__range=(dt_start, dt_end)) & ~Q(created_at__isnull=True))
        ).distinct().prefetch_related("lignes")

        stats = {
            "tickets_count": 0,
            "winners_count": 0,
            "total_gain_du": Decimal("0"),
        }

        now = timezone.now()

        for ticket in tickets:
            stats["tickets_count"] += 1
            ticket_gain_du = Decimal("0")
            ticket_is_winner = False

            for line in ticket.lignes.all():
                gain_du, is_winner, win_context = ResultCalculationService._calculate_line(
                    line=line,
                    lot1=lot1,
                    lot2=lot2,
                    lot3=lot3,
                    loto3_val=loto3_val,
                    loto4_vals=loto4_vals,
                    loto5_vals=loto5_vals,
                    coeff_1er=coeff_1er,
                    coeff_2eme=coeff_2eme,
                    coeff_3eme=coeff_3eme,
                    coeff_loto3=coeff_loto3,
                    coeff_loto4=coeff_loto4,
                    coeff_loto5=coeff_loto5,
                    coeff_mariage=coeff_mariage,
                    payout_mariage_gratuit=payout_mariage_gratuit,
                )

                # Mise à jour de la ligne (idempotent)
                line.gain_du = gain_du
                line.is_winner = is_winner
                line.win_context = win_context
                line.save(update_fields=["gain_du", "is_winner", "win_context"])

                ticket_gain_du += gain_du
                if is_winner:
                    ticket_is_winner = True

            # Mise à jour du ticket
            ticket.total_gain_du = ticket_gain_du
            ticket.is_winner = ticket_is_winner
            ticket.computed_at = now
            update_fields = ["total_gain_du", "is_winner", "computed_at"]
            if resultat.session_key and ticket.tirage_session_key != resultat.session_key:
                ticket.tirage_session_key = resultat.session_key
                update_fields.append("tirage_session_key")
            ticket.save(update_fields=update_fields)

            if ticket_is_winner:
                stats["winners_count"] += 1
            stats["total_gain_du"] += ticket_gain_du

        # Marquer le résultat comme calculé
        resultat.computed_at = now
        resultat.save(update_fields=["computed_at"])

        return stats

    @staticmethod
    def _calculate_line(
        *,
        line,
        lot1: str,
        lot2: str,
        lot3: str,
        loto3_val: str,
        loto4_vals,
        loto5_vals,
        coeff_1er: Decimal,
        coeff_2eme: Decimal,
        coeff_3eme: Decimal,
        coeff_loto3: Decimal,
        coeff_loto4: Decimal,
        coeff_loto5: Decimal,
        coeff_mariage: Decimal,
        payout_mariage_gratuit: Decimal,
        **kwargs,
    ) -> tuple[Decimal, bool, str]:
        """
        Calcule le gain pour une ligne de ticket.
        
        Returns:
            (gain_du, is_winner, win_context)
        """
        jeu = (line.jeu or "").strip().lower()
        valeur = (line.valeur or "").strip()
        mise = line.mise or Decimal("0")

        if mise <= 0 and not line.gratuit:
            return Decimal("0"), False, ""

        # Pour les mariages gratuits, on utilise une mise fictive pour le calcul
        effective_mise = mise if mise > 0 else Decimal("1")

        if jeu == "boule":
            return ResultCalculationService._calc_boule(
                valeur=valeur,
                mise=effective_mise,
                lot1=lot1,
                lot2=lot2,
                lot3=lot3,
                coeff_1er=coeff_1er,
                coeff_2eme=coeff_2eme,
                coeff_3eme=coeff_3eme,
            )

        elif jeu == "mariage":
            return ResultCalculationService._calc_mariage(
                valeur=valeur,
                mise=effective_mise,
                lot1=lot1,
                lot2=lot2,
                lot3=lot3,
                coeff_mariage=coeff_mariage,
                is_gratuit=line.gratuit,
                payout_mariage_gratuit=payout_mariage_gratuit,
            )

        elif jeu == "loto3":
            return ResultCalculationService._calc_loto3(
                valeur=valeur,
                mise=effective_mise,
                loto3_val=loto3_val,
                coeff_loto3=coeff_loto3,
            )

        elif jeu == "loto4":
            return ResultCalculationService._calc_loto4(
                valeur=valeur,
                mise=effective_mise,
                loto4_vals=loto4_vals,
                coeff_loto4=coeff_loto4,
            )

        elif jeu == "loto5":
            return ResultCalculationService._calc_loto5(
                valeur=valeur,
                mise=effective_mise,
                loto5_vals=loto5_vals,
                coeff_loto5=coeff_loto5,
            )

        return Decimal("0"), False, ""

    @staticmethod
    def _calc_boule(
        *,
        valeur: str,
        mise: Decimal,
        lot1: str,
        lot2: str,
        lot3: str,
        coeff_1er: Decimal,
        coeff_2eme: Decimal,
        coeff_3eme: Decimal,
    ) -> tuple[Decimal, bool, str]:
        """Boule gagne si numéro == lot1/lot2/lot3.
        Si le numéro sort dans plusieurs lots (ex: 1er et 2ème lot),
        les gains s'additionnent (1er lot + 2ème lot).
        """
        v = (valeur or "").strip().zfill(2)
        l1 = (lot1 or "").strip().zfill(2) if lot1 else ""
        l2 = (lot2 or "").strip().zfill(2) if lot2 else ""
        l3 = (lot3 or "").strip().zfill(2) if lot3 else ""

        if not v or not (l1 or l2 or l3):
            return Decimal("0"), False, ""

        total_gain = Decimal("0")
        contexts = []

        if l1 and v == l1:
            total_gain += mise * coeff_1er
            contexts.append("1er lot")
        if l2 and v == l2:
            total_gain += mise * coeff_2eme
            contexts.append("2ème lot")
        if l3 and v == l3:
            total_gain += mise * coeff_3eme
            contexts.append("3ème lot")

        if contexts:
            return total_gain, True, " + ".join(contexts)

        return Decimal("0"), False, ""

    @staticmethod
    def _calc_mariage(
        *,
        valeur: str,
        mise: Decimal,
        lot1: str,
        lot2: str,
        lot3: str,
        coeff_mariage: Decimal,
        is_gratuit: bool,
        payout_mariage_gratuit: Decimal,
    ) -> tuple[Decimal, bool, str]:
        """Mariage gagne si les deux numéros forment l'une des 3 paires du tirage:
        (lot1, lot2), (lot1, lot3), (lot2, lot3).
        Si une paire est présente plusieurs fois (lots dupliqués), les gains s'additionnent.
        """
        parts = (valeur or "").replace("-", "x").split("x")
        if len(parts) != 2:
            return Decimal("0"), False, ""

        n1 = parts[0].strip().zfill(2)
        n2 = parts[1].strip().zfill(2)
        if not n1 or not n2:
            return Decimal("0"), False, ""

        l1 = (lot1 or "").strip().zfill(2) if lot1 else ""
        l2 = (lot2 or "").strip().zfill(2) if lot2 else ""
        l3 = (lot3 or "").strip().zfill(2) if lot3 else ""

        pairs = []
        if l1 and l2:
            pairs.append((l1, l2))
        if l1 and l3:
            pairs.append((l1, l3))
        if l2 and l3:
            pairs.append((l2, l3))

        if not pairs:
            return Decimal("0"), False, ""

        matches_count = 0
        for p1, p2 in pairs:
            if (n1 == p1 and n2 == p2) or (n1 == p2 and n2 == p1):
                matches_count += 1

        if matches_count > 0:
            unit_gain = mise * coeff_mariage if not is_gratuit else payout_mariage_gratuit
            total_gain = unit_gain * matches_count
            context = "Mariage gagnant" if matches_count == 1 else f"Mariage gagnant ({matches_count}x)"
            return total_gain, True, context

        return Decimal("0"), False, ""

    @staticmethod
    def _calc_loto3(
        *,
        valeur: str,
        mise: Decimal,
        loto3_val: str,
        coeff_loto3: Decimal,
    ) -> tuple[Decimal, bool, str]:
        """Loto3 gagne si valeur == loto3 du résultat."""
        v = (valeur or "").strip().zfill(3)
        target = (loto3_val or "").strip().zfill(3) if loto3_val else ""
        if v and target and v == target:
            return mise * coeff_loto3, True, "Loto3"
        return Decimal("0"), False, ""

    @staticmethod
    def _calc_loto4(
        *,
        valeur: str,
        mise: Decimal,
        loto4_vals,
        coeff_loto4: Decimal,
    ) -> tuple[Decimal, bool, str]:
        """Loto4 gagne si valeur == une des options loto4. Si options dupliquées, s'additionnent."""
        v = (valeur or "").strip().zfill(4)
        if not v:
            return Decimal("0"), False, ""

        opts = [str(opt).strip().zfill(4) for opt in loto4_vals if opt and len(str(opt).strip()) >= 2]
        matches_count = sum(1 for opt in opts if opt == v)
        if matches_count > 0:
            total_gain = matches_count * mise * coeff_loto4
            context = "Loto4" if matches_count == 1 else f"Loto4 ({matches_count}x)"
            return total_gain, True, context

        return Decimal("0"), False, ""

    @staticmethod
    def _calc_loto5(
        *,
        valeur: str,
        mise: Decimal,
        loto5_vals,
        coeff_loto5: Decimal,
    ) -> tuple[Decimal, bool, str]:
        """Loto5 gagne si valeur == une des options loto5. Si options dupliquées, s'additionnent."""
        v = (valeur or "").strip().zfill(5)
        if not v:
            return Decimal("0"), False, ""

        opts = [str(opt).strip().zfill(5) for opt in loto5_vals if opt and len(str(opt).strip()) >= 2]
        matches_count = sum(1 for opt in opts if opt == v)
        if matches_count > 0:
            total_gain = matches_count * mise * coeff_loto5
            context = "Loto5" if matches_count == 1 else f"Loto5 ({matches_count}x)"
            return total_gain, True, context

        return Decimal("0"), False, ""

    @staticmethod
    @transaction.atomic
    def calculate_single_ticket_gains(ticket: Ticket) -> None:
        """
        Calcule les gains pour un ticket spécifique UNIQUEMENT si son tirage est FERMÉ
        et qu'un résultat officiel validé existe pour sa session ou sa date.
        """
        from accounts.models import Resultat, AdminPaymentSettings
        from agent_portal.models import TicketStatus

        if ticket.statut != TicketStatus.VALIDE or not ticket.tirage:
            return

        # RÈGLE MÉTIER ABSOLUE 1 : Si le tirage est OUVERT, aucun gain ne peut exister !
        if ticket.tirage.etat_ouverture == "OUVERT":
            if ticket.is_winner or (ticket.total_gain_du and ticket.total_gain_du > Decimal("0")) or ticket.computed_at is not None:
                ticket.is_winner = False
                ticket.total_gain_du = Decimal("0.00")
                ticket.computed_at = None
                ticket.save(update_fields=["is_winner", "total_gain_du", "computed_at"])
                ticket.lignes.update(gain_du=Decimal("0.00"), is_winner=False, win_context="")
            return

        # RÈGLE MÉTIER 2 : Le tirage est FERMÉ. On cherche le résultat correspondant.
        resultat = None
        # Priorité 1 : Match par session_key exacte du ticket
        if ticket.tirage_session_key:
            resultat = Resultat.objects.filter(
                tirage=ticket.tirage,
                session_key=ticket.tirage_session_key,
            ).exclude(statut="rejected").first()

        # Priorité 2 : Pour tickets hors ligne synchronisés, match UNIQUEMENT sur la même date locale
        if not resultat and ticket.created_at:
            ticket_date = timezone.localtime(ticket.created_at).date()
            resultat = Resultat.objects.filter(
                tirage=ticket.tirage,
                date=ticket_date,
            ).exclude(statut="rejected").order_by("-id").first()

        if not resultat or not (resultat.lot1 and resultat.lot2 and resultat.lot3):
            # Pas encore de résultat complet officiel saisi : le ticket reste non calculé (0 gain)
            if ticket.is_winner or (ticket.total_gain_du and ticket.total_gain_du > Decimal("0")) or ticket.computed_at is not None:
                ticket.is_winner = False
                ticket.total_gain_du = Decimal("0.00")
                ticket.computed_at = None
                ticket.save(update_fields=["is_winner", "total_gain_du", "computed_at"])
                ticket.lignes.update(gain_du=Decimal("0.00"), is_winner=False, win_context="")
            return

        # Récupérer les coefficients de paiement
        try:
            settings = AdminPaymentSettings.objects.get(borlette=ticket.tirage.borlette)
        except AdminPaymentSettings.DoesNotExist:
            settings = None

        coeff_1er = Decimal(str(getattr(settings, "boule_1er_lot_coeff", 0) or 0))
        coeff_2eme = Decimal(str(getattr(settings, "boule_2eme_lot_coeff", 0) or 0))
        coeff_3eme = Decimal(str(getattr(settings, "boule_3eme_lot_coeff", 0) or 0))
        coeff_loto3 = Decimal(str(getattr(settings, "loto3_coeff", 0) or 0))
        coeff_loto4 = Decimal(str(getattr(settings, "loto4_coeff", 0) or 0))
        coeff_loto5 = Decimal(str(getattr(settings, "loto5_coeff", 0) or 0))
        coeff_mariage = Decimal(str(getattr(settings, "mariage_normal_coeff", 0) or 0))
        payout_mariage_gratuit = Decimal(str(getattr(settings, "mariage_gratuit_montant_fixe", 0) or 0))

        # Extraire les numéros gagnants du résultat
        lot1 = (resultat.lot1 or "").strip()
        lot2 = (resultat.lot2 or "").strip()
        lot3 = (resultat.lot3 or "").strip()
        loto3_val = resultat.loto3
        loto4_vals = [resultat.loto4_opt1, resultat.loto4_opt2, resultat.loto4_opt3]
        loto5_vals = [resultat.loto5_opt1, resultat.loto5_opt2, resultat.loto5_opt3]

        ticket_gain_du = Decimal("0")
        ticket_is_winner = False

        for line in ticket.lignes.all():
            gain_du, is_winner, win_context = ResultCalculationService._calculate_line(
                line=line,
                lot1=lot1,
                lot2=lot2,
                lot3=lot3,
                loto3_val=loto3_val,
                loto4_vals=loto4_vals,
                loto5_vals=loto5_vals,
                coeff_1er=coeff_1er,
                coeff_2eme=coeff_2eme,
                coeff_3eme=coeff_3eme,
                coeff_loto3=coeff_loto3,
                coeff_loto4=coeff_loto4,
                coeff_loto5=coeff_loto5,
                coeff_mariage=coeff_mariage,
                payout_mariage_gratuit=payout_mariage_gratuit,
            )

            # Mise à jour de la ligne (idempotent)
            line.gain_du = gain_du
            line.is_winner = is_winner
            line.win_context = win_context
            line.save(update_fields=["gain_du", "is_winner", "win_context"])

            ticket_gain_du += gain_du
            if is_winner:
                ticket_is_winner = True

        # Mise à jour du ticket
        ticket.total_gain_du = ticket_gain_du
        ticket.is_winner = ticket_is_winner
        ticket.computed_at = timezone.now()
        update_fields = ["total_gain_du", "is_winner", "computed_at"]
        if resultat.session_key and ticket.tirage_session_key != resultat.session_key:
            ticket.tirage_session_key = resultat.session_key
            update_fields.append("tirage_session_key")
        ticket.save(update_fields=update_fields)
