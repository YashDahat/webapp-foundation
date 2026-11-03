/**
 * Public loyalty programme terms (compliance): how the Loyalty Score and Reward Coins work, coin value +
 * expiry, that coins are a discount and not cash, our right to change the programme with notice, and the
 * data/consent position (DPDP). Linked from the account rewards card and the footer.
 */
export default function LoyaltyTermsPage() {
  return (
    <div className="mx-auto max-w-2xl space-y-6 px-4 py-10">
      <h1 className="text-2xl font-bold">Rewards programme terms</h1>

      <section className="space-y-2">
        <h2 className="text-lg font-semibold">Loyalty Score &amp; tiers</h2>
        <p className="text-sm text-muted-foreground">
          Your Loyalty Score reflects how you engage with us (shopping, reviews, referrals). It sets your tier
          and the perks you see. The score is never spent and fades gradually over time, so it always reflects
          your recent activity.
        </p>
      </section>

      <section className="space-y-2">
        <h2 className="text-lg font-semibold">Reward Coins</h2>
        <ul className="list-disc space-y-1 pl-5 text-sm text-muted-foreground">
          <li>Coins are earned from purchases, reviews and referrals.</li>
          <li>1 coin is worth ₹0.50 off a future order.</li>
          <li>Coins can pay for up to 10% of an order, on orders of ₹500 or more.</li>
          <li>Coins become usable after the return window closes and expire 12 months after they’re earned.</li>
          <li>Coins and points earned on an order are reversed if that order is returned or cancelled.</li>
          <li>
            <strong>Coins are a discount, not money.</strong> They have no cash value and cannot be exchanged
            for cash or transferred between accounts.
          </li>
        </ul>
      </section>

      <section className="space-y-2">
        <h2 className="text-lg font-semibold">Discount codes</h2>
        <p className="text-sm text-muted-foreground">
          Personal codes are tied to your account and are single-use. Any expiry date shown is the code’s real
          expiry — we don’t use fake countdowns or false “almost gone” claims.
        </p>
      </section>

      <section className="space-y-2">
        <h2 className="text-lg font-semibold">Your data &amp; choices</h2>
        <p className="text-sm text-muted-foreground">
          We only tailor rewards to your activity with your consent, which you can turn off any time from your
          account. Turning it off stops personalised offers; your tier perks continue.
        </p>
      </section>

      <section className="space-y-2">
        <h2 className="text-lg font-semibold">Changes</h2>
        <p className="text-sm text-muted-foreground">
          We may change or end the rewards programme with reasonable notice. These terms are a summary and may
          be updated; the current version always applies.
        </p>
      </section>
    </div>
  );
}
