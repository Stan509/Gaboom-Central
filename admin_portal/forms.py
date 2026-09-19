from django import forms
from django.contrib.auth.forms import AuthenticationForm

from accounts.models import Borlette, User, SousDirecteur, Agent
from django.contrib.auth import authenticate


class PortalAuthenticationForm(AuthenticationForm):
    username = forms.CharField(
        label="Nom d'utilisateur ou Téléphone",
        widget=forms.TextInput(attrs={"autocomplete": "username", "placeholder": "Nom d'utilisateur ou téléphone"}),
    )
    password = forms.CharField(
        label="Mot de passe",
        strip=False,
        widget=forms.PasswordInput(attrs={"autocomplete": "current-password", "placeholder": "Mot de passe"}),
    )

    def clean(self):
        raw_username = (self.cleaned_data.get("username") or "").strip()
        password = self.cleaned_data.get("password")

        if raw_username and password:
            # 1. Direct standard authentication
            self.user_cache = authenticate(
                self.request, username=raw_username, password=password
            )

            # 2. Case-insensitive username match
            if self.user_cache is None:
                matched_user = User.objects.filter(username__iexact=raw_username).first()
                if matched_user:
                    self.user_cache = authenticate(
                        self.request, username=matched_user.username, password=password
                    )

            # 3. Match by SousDirecteur phone number
            if self.user_cache is None:
                sd = SousDirecteur.objects.filter(telephone__iexact=raw_username).select_related("user").first()
                if not sd:
                    digits = "".join(filter(str.isdigit, raw_username))
                    if digits and len(digits) >= 6:
                        for candidate in SousDirecteur.objects.all().select_related("user"):
                            cand_digits = "".join(filter(str.isdigit, candidate.telephone or ""))
                            if cand_digits and (cand_digits == digits or cand_digits.endswith(digits) or digits.endswith(cand_digits)):
                                sd = candidate
                                break
                if sd and sd.user:
                    self.user_cache = authenticate(
                        self.request, username=sd.user.username, password=password
                    )

            # 4. Match by Agent phone number
            if self.user_cache is None:
                agent = Agent.objects.filter(telephone__iexact=raw_username).select_related("user").first()
                if not agent:
                    digits = "".join(filter(str.isdigit, raw_username))
                    if digits and len(digits) >= 6:
                        for candidate in Agent.objects.all().select_related("user"):
                            cand_digits = "".join(filter(str.isdigit, candidate.telephone or ""))
                            if cand_digits and (cand_digits == digits or cand_digits.endswith(digits) or digits.endswith(cand_digits)):
                                agent = candidate
                                break
                if agent and agent.user:
                    self.user_cache = authenticate(
                        self.request, username=agent.user.username, password=password
                    )

            # 5. Match by Email
            if self.user_cache is None and "@" in raw_username:
                email_user = User.objects.filter(email__iexact=raw_username).first()
                if email_user:
                    self.user_cache = authenticate(
                        self.request, username=email_user.username, password=password
                    )

            if self.user_cache is None:
                raise self.get_invalid_login_error()
            else:
                self.confirm_login_allowed(self.user_cache)

        return self.cleaned_data


class BorletteInfoForm(forms.ModelForm):
    class Meta:
        model = Borlette
        fields = [
            "nom_borlette",
            "telephone",
            "adresse",
            "site_web",
            "slogan",
            "logo_borlette",
            "ticket_footer_text",
            "mariage_gratuit_actif",
            "mariage_gratuit_montant",
        ]

        widgets = {
            "nom_borlette": forms.TextInput(attrs={"class": "gaboom-input w-full"}),
            "telephone": forms.TextInput(attrs={"class": "gaboom-input w-full"}),
            "adresse": forms.Textarea(attrs={"class": "gaboom-input w-full", "rows": 4}),
            "site_web": forms.URLInput(attrs={"class": "gaboom-input w-full"}),
            "slogan": forms.TextInput(attrs={"class": "gaboom-input w-full"}),
            "logo_borlette": forms.ClearableFileInput(attrs={"class": "gaboom-input w-full"}),
            "ticket_footer_text": forms.Textarea(attrs={"class": "gaboom-input w-full", "rows": 3}),
            "mariage_gratuit_montant": forms.NumberInput(attrs={"class": "gaboom-input w-full", "step": "0.01"}),
        }

