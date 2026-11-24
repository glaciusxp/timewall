# Privacy e attribuzione — 0.101

La schermata Info → Privacy ora mostra un'informativa leggibile offline, nelle
nove lingue supportate. Sostituisce il precedente messaggio di preparazione.

- Titolare: Andrea Persano.
- Contatto: andreapersano@gmail.com (indicato dall'autore, indipendente dai dati Git).
- Aggiornamento: 2026-11-20.
- URL pubblico: non ancora definito. PrivacyNotice.WEBSITE è vuoto: non si apre
  alcun indirizzo fittizio e si mostra il testo tradotto «da definire».

PrivacyNotice compone il testo dalle risorse strings.xml e mantiene identità,
contatto e data separati dalle traduzioni. Info rende cliccabili email e link web.
L'informativa copre funzioni e base giuridica, iCal completi e URL segreti, richieste
Open-Meteo/geocoding, IP e log del provider, impostazioni e immagini locali,
conservazione/cancellazione, sicurezza e diritti applicabili. Non viene aggiunto
un generico consenso obbligatorio all'avvio né vengono cambiati i flussi di rete.

Il testo riflette l'implementazione corrente, inclusa la pulizia cache della 0.100.
Per il servizio API gratuito Open-Meteo dichiara log anche con coordinate eliminati
dopo 90 giorni; è una dichiarazione del provider, non una verifica del suo server.
I messaggi inviati volontariamente al contatto sono distinti dai dati conservati
solo sul dispositivo. Le informative dei provider iCal configurabili dall'utente
non vengono sostituite dalla policy TimeWall.

DashboardLayout aggiunge nel blocco meteo «Dati meteo: Open-Meteo · CC BY 4.0»,
con link HTTPS separati a Open-Meteo e alla licenza dati. Le icone originali non
vengono attribuite al provider o a FontAwesome. Il credito usa il colore secondario
del tema, resta visibile con dati meteo o stato di errore e sparisce con il blocco
meteo disabilitato. I link si aprono solo con un tap e non fanno richieste API
aggiuntive; l'assenza di un browser viene gestita senza crash.

## Passaggi ancora necessari prima dello store

Pubblicare l'informativa su una pagina web pubblica e fornire il suo URL effettivo;
poi impostare PrivacyNotice.WEBSITE e inserire il link in Play Console. Il testo
in-app non sostituisce la pagina esterna obbligatoria né il questionario Data safety.
Confermare che il trattamento operativo dei messaggi di contatto corrisponda ai
criteri dichiarati e riesaminare il testo quando cambiano funzioni o fornitori.

Fonti consultate il 20 novembre 2026:
https://open-meteo.com/en/terms
https://github.com/open-meteo/open-meteo/blob/main/README.md
https://creativecommons.org/licenses/by/4.0/
https://www.garanteprivacy.it/home/i-miei-diritti/diritti
https://support.google.com/googleplay/android-developer/answer/10144311

Test automatici: parità delle nove lingue, segnaposto del titolare/contatto,
presenza delle sezioni privacy e marcatori dei due link in ogni attribuzione.
Nessun test visivo o funzionale viene eseguito sul tablet dopo l'installazione.
