(() => {
  const body = document.body;
  const overview = document.getElementById('overview');
  const comparison = document.getElementById('comparison');
  const prototypes = [...document.querySelectorAll('.prototype')];
  const switches = [...document.querySelectorAll('.variant-switch')];
  const modeToggle = document.getElementById('modeToggle');

  function showTarget(id, updateUrl = true) {
    const allowed = ['overview', 'variant-a', 'variant-b', 'variant-c'];
    const target = allowed.includes(id) ? id : 'overview';
    const isOverview = target === 'overview';
    overview.style.display = isOverview ? '' : 'none';
    comparison.style.display = isOverview ? '' : 'none';
    prototypes.forEach((prototype) => prototype.classList.toggle('active', prototype.id === target));
    switches.forEach((button) => button.setAttribute('aria-pressed', String(button.dataset.target === target)));
    if (updateUrl) {
      const url = new URL(window.location.href);
      url.searchParams.set('view', target);
      history.replaceState(null, '', url);
    }
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  switches.forEach((button) => button.addEventListener('click', () => showTarget(button.dataset.target)));

  document.querySelectorAll('[data-open]').forEach((card) => {
    const open = () => showTarget(card.dataset.open);
    card.addEventListener('click', open);
    card.addEventListener('keydown', (event) => {
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        open();
      }
    });
  });

  document.querySelectorAll('.screen-tabs').forEach((group) => {
    group.addEventListener('click', (event) => {
      const button = event.target.closest('.screen-tab');
      if (!button) return;
      const stage = group.closest('.stage');
      group.querySelectorAll('.screen-tab').forEach((item) => item.classList.toggle('active', item === button));
      stage.querySelectorAll('.screen').forEach((screen) => screen.classList.toggle('active', screen.id === button.dataset.screen));
    });
  });

  document.querySelectorAll('.stepper').forEach((group) => {
    group.addEventListener('click', (event) => {
      const button = event.target.closest('.step-tab');
      if (!button) return;
      const screen = group.closest('.screen');
      group.querySelectorAll('.step-tab').forEach((item) => item.classList.toggle('active', item === button));
      screen.querySelectorAll('.step-panel').forEach((panel) => panel.classList.toggle('active', panel.id === button.dataset.step));
    });
  });

  document.querySelectorAll('.advance-card').forEach((card) => {
    card.addEventListener('click', () => {
      const panel = card.closest('.step-panel');
      panel.querySelectorAll('.advance-card').forEach((item) => item.classList.remove('selected'));
      card.classList.add('selected');
    });
  });

  modeToggle.addEventListener('click', () => {
    const dark = body.dataset.mode === 'dark';
    body.dataset.mode = dark ? 'light' : 'dark';
    modeToggle.textContent = dark ? '暗色' : '亮色';
    const url = new URL(window.location.href);
    url.searchParams.set('mode', body.dataset.mode);
    history.replaceState(null, '', url);
  });

  const initial = new URLSearchParams(window.location.search);
  if (initial.get('mode') === 'dark') {
    body.dataset.mode = 'dark';
    modeToggle.textContent = '亮色';
  }
  showTarget(initial.get('view') || 'overview', false);
})();
