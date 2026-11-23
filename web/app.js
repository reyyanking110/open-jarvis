document.addEventListener("DOMContentLoaded", () => {
  const copyButtons = document.querySelectorAll("[data-copy-target]");
  copyButtons.forEach((button) => {
    button.addEventListener("click", async () => {
      const targetId = button.getAttribute("data-copy-target");
      const target = targetId ? document.getElementById(targetId) : null;
      if (!target) return;

      try {
        await navigator.clipboard.writeText(target.textContent.trim());
        const original = button.textContent;
        button.textContent = "Copied";
        setTimeout(() => {
          button.textContent = original;
        }, 1600);
      } catch {
        button.textContent = "Copy manually";
      }
    });
  });

  const tabButtons = document.querySelectorAll(".tab-btn[data-filter]");
  const modelCards = document.querySelectorAll(".model-card[data-category]");

  tabButtons.forEach((tab) => {
    tab.addEventListener("click", () => {
      const filter = tab.getAttribute("data-filter");

      tabButtons.forEach((item) => {
        const isActive = item === tab;
        item.classList.toggle("active", isActive);
        item.setAttribute("aria-selected", String(isActive));
      });

      modelCards.forEach((card) => {
        const category = card.getAttribute("data-category");
        card.hidden = filter !== "all" && category !== filter;
      });
    });
  });
});
