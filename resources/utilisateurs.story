Scénario : Créer un utilisateur
Etant donné que l'utilisateur <mail1> n'existe pas
Quand je crée l'utilisateur <mail1>
Alors l'utilisateur <mail1> existe dans le repository des utilisateurs
Exemples :
|mail1                     |
|user1@company.com         |
|user2@company.com         |

Scénario : Associer un profil fournisseur à un utilisateur
Etant donné que l'utilisateur <mail1> existe
Etant donné que le profil fournisseur <profil1> existe
Quand j'associe le profil fournisseur <profil1> à l'utilisateur <mail1>
Alors l'utilisateur <mail1> a le profil fournisseur <profil1>