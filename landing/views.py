from django.http import HttpRequest
from django.shortcuts import render
from django.core.paginator import Paginator
from accounts.models import DocumentationVideo


def index(request: HttpRequest):
    from accounts.models import Tirage, Resultat, TirageStatus

    all_recent_results = (
        Resultat.objects.filter(tirage__statut=TirageStatus.ACTIF)
        .select_related("tirage")
        .order_by("-date", "-id")
    )

    seen_draw_names = set()
    latest_three_results = []

    for res in all_recent_results:
        draw_key = res.tirage.nom.strip().lower()
        if draw_key not in seen_draw_names:
            seen_draw_names.add(draw_key)
            latest_three_results.append(res)
            if len(latest_three_results) >= 3:
                break

    return render(request, "landing/index.html", {
        "latest_results": latest_three_results
    })


def documentation(request: HttpRequest):
    """Page de documentation avec vidéos YouTube intégrées par catégorie."""
    videos = DocumentationVideo.objects.filter(is_active=True).order_by('order', 'created_at')
    
    # Organiser les vidéos par catégorie
    intro_videos = videos.filter(category__iexact='Introduction')
    config_videos = videos.filter(category__iexact='Configuration')
    admin_videos = videos.filter(category__iexact='Admin Borlette') | videos.filter(category__iexact='Admin Borlettes')
    agents_videos = videos.filter(category__iexact='Agents')
    affiliate_videos = videos.filter(category__iexact='Affiliation') | videos.filter(category__iexact='Affiliés')
    resume_videos = videos.filter(category__iexact='Résumé') | videos.filter(category__iexact='Resume')
    
    return render(request, "landing/documentation.html", {
        'intro_videos': intro_videos.order_by('order', 'created_at'),
        'config_videos': config_videos.order_by('order', 'created_at'),
        'admin_videos': admin_videos.order_by('order', 'created_at'),
        'agents_videos': agents_videos.order_by('order', 'created_at'),
        'affiliate_videos': affiliate_videos.order_by('order', 'created_at'),
        'resume_videos': resume_videos.order_by('order', 'created_at'),
    })
