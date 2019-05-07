Feature: cash withdrawal
	As a card holder of a card emitted by a french bank
	I want to withdraw cash in France
	In order to get cash rapidly anywhere and anytime

Scenario: withdrawal with a payment card at an ATM - success withdrawal accepted
Given the card holder "Jeremie" has the card 1234567890123456 with a 1000 € balance
When the card holder withdraw 200 € at the ATM rue de l'université
Then he gets 200 € in cash
Then the account balance is 800 €

Scenario: withdrawal with a payment card at an ATM - failure insufficient balance
Given the card holder "Jeremie" has the card 1234567890123456 with a 50 € balance
# rule insufficient_balance
When the card holder withdraw 200 € at the ATM rue de l'université
Then he gets the message "insufficient balance"
Then the account balance is 50 €

#Rule: insufficient_balance
# When <account.balance < withdrawal.amount>
# Then <throw error "Insufficient balance">

