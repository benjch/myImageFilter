# Nom du projet  

Courte description du projet : objectif, contexte ou problème résolu.  

---

## 🚀 Fonctionnalités  

- Fonctionnalité principale 1  
- Fonctionnalité principale 2  
- Fonctionnalité à venir  

---

## 🛠️ Technologies utilisées  

- Java (version 11/17/21 selon ton projet)  
- Maven ou Gradle  
- Spring Boot (si applicable)  
- Base de données (MySQL, PostgreSQL, MongoDB, etc.)  

---

## 📦 Installation et exécution  

### Prérequis  
- Java installé (version >= 11)  
- Maven ou Gradle installé  

### Étapes  
~~~~bash
# Cloner le projet
git clone https://github.com/utilisateur/mon-projet-java.git
cd mon-projet-java

# Construire le projet avec Maven
mvn clean install

# Lancer l’application
mvn spring-boot:run
~~~~

---

## ⚙️ Configuration  

Les variables de configuration sont définies dans `application.properties` ou `application.yml`.  

Exemple :  
~~~~properties
spring.datasource.url=jdbc:postgresql://localhost:5432/ma_base
spring.datasource.username=mon_user
spring.datasource.password=mon_password
~~~~

---

## 🧪 Tests  

Exécuter les tests unitaires et d’intégration :  
~~~~bash
mvn test
~~~~

---

## 📚 Documentation API  

Une fois l’application lancée, l’API est accessible à :  
- API : http://localhost:8080/api/  
- Swagger (si activé) : http://localhost:8080/swagger-ui.html  

---

## 👨‍💻 Contributeurs  

- Nom Prénom – Rôle – [Profil GitHub/LinkedIn]  

---

## 📄 Licence  

Ce projet est distribué sous licence **MIT**.  
Voir le fichier LICENSE pour plus de détails.  

---

## 🚀 Déploiement du serveur  

Script PowerShell complet à copier-coller :  

~~~~powershell
Enter-PSSession -ComputerName 100.77.140.100 -Credential (Get-Credential 'NAS5IEME\Benjamin')

# Stop process qui écoute sur 8080
$c = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if ($c) { Stop-Process -Id $c.OwningProcess -Force }

# Nettoyer les répertoires distants
Remove-Item -Path "D:\program\protoServer\bin" -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -Path "D:\program\protoServer\cfg" -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -Path "D:\program\protoServer\lib" -Recurse -Force -ErrorAction SilentlyContinue

# Recréer les répertoires
New-Item -ItemType Directory -Path "D:\program\protoServer\bin" -Force | Out-Null
New-Item -ItemType Directory -Path "D:\program\protoServer\cfg" -Force | Out-Null
New-Item -ItemType Directory -Path "D:\program\protoServer\lib" -Force | Out-Null

# Copier depuis le client (chemins locaux) vers la machine distante ? à lancer AVANT Enter-PSSession
# Exemple (côté client) :
# Copy-Item "C:\Users\NR5145\HD_D\benjch\gitBenjch\time-machine-benjch\target\jreleaser\assemble\app\java-archive\work\app-0.0.1-SNAPSHOT\bin\*" D:\program\protoServer\bin -Recurse -Force -ToSession $session

# Démarrer le serveur
Start-Process "D:\program\protoServer\run-server.bat"
~~~~
