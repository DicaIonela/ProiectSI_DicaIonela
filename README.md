# MultiAgentBank - Sistem Bancar Multi-Agent

## Descrierea Problemei

Aplicația implementează un sistem bancar distribuit folosind tehnologia multi-agent JADE. Sistemul constă din:
- **CentralBank**: Agent central care gestionează baza de date cu conturi și validează toate tranzacțiile
- **BranchAgent**: Agenți filială care oferă interfață grafică pentru clienți (depuneri, retrageri, autentificare)
- **ShutdownController**: Agent de administrare care orchestrează oprirea ordonată a sistemului

Comunicarea între agenți se realizează prin protocoale FIPA ACL (Request-Reply și Publish-Subscribe), asigurând sincronizarea în timp real a soldurilor între toate filialele.

## Cerințe de Sistem

- **Java**: JDK 1.8 sau superior
- **IDE**: Eclipse (recomandat) sau IntelliJ IDEA

**Notă**: Toate bibliotecile JADE necesare sunt incluse în proiect.

## Instalare

1. Se descarcă arhiva și se dezarhivează.

2. **Import în Eclipse**:
   - Deschideți Eclipse și selectați un workspace
   - Navigați la `File > Import > General > Projects from Folder or Archive`
   - Click `Directory` și selectați folderul `MultiAgentBank` clonat
   - Click `Finish`

## Configurare

1. Click dreapta pe proiect în Package Explorer
2. Selectați `Run As > Run Configurations...`
3. Click dreapta pe `Java Application` > `New Configuration`
4. Setați următoarele:
   - **Main Class**: `jade.Boot`

5. În tab-ul `Arguments`, secțiunea `Program arguments`, introduceți:
```
-gui CentralBank:multiagentbank.BankAgent;Branch1:multiagentbank.BranchAgent;Branch2:multiagentbank.BranchAgent;Branch3:multiagentbank.BranchAgent;ShutdownController:multiagentbank.ShutdownAgent
```

6. Click `Apply` apoi `Run`

## Lansare în Execuție

După configurare, sistemul se lansează automat cu:
- **JADE GUI**: Consolă de monitorizare a agenților (include Sniffer pentru vizualizarea comunicării)
- **Central Bank Server**: Fereastra băncii centrale cu log-ul tranzacțiilor
- **Branch1, Branch2, Branch3**: Trei ferestre de filială pentru clienți
- **Shutdown Controller**: Panoul de control pentru oprirea sistemului

## Utilizare

### Operații Client (BranchAgent):
1. **Creare Cont**: Click pe "CREATE NEW ACCOUNT", introduceți PIN (minim 4 caractere)
2. **Autentificare**: Introduceți numărul de cont și PIN-ul, apoi click "LOGIN"
3. **Depunere**: Introduceți suma în câmpul "Amount" și click "DEPOSIT"
4. **Retragere**: Introduceți suma și click "WITHDRAW"
5. **Deconectare**: Click "LOGOUT"

### Monitorizare Comunicare (JADE Sniffer):
1. În fereastra JADE GUI, selectați toți agenții (Ctrl+Click)
2. Click dreapta > `Start Sniffer Agent`
3. Vizualizați mesajele REQUEST/INFORM în timp real

### Oprire Sistem:
1. În fereastra "Shutdown Controller"
2. Click pe butonul roșu "SHUTDOWN SYSTEM"
3. Confirmați acțiunea
4. Sistemul se va închide automat în ordine: Filiale → Bancă → Controller

## Persistență Date

Conturile și soldurile sunt salvate automat în fișierul `bank_accounts.dat` la fiecare tranzacție și la shutdown. La următoarea pornire, datele vor fi restaurate automat.

## Suport

Pentru probleme sau întrebări:
- Documentație JADE: https://jade.tilab.com/documentation/
- Issues: https://github.com/username/MultiAgentBank/issues
