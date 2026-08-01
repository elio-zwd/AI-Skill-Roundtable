(() => {
  const body = document.body;
  const modeToggle = document.getElementById('modeToggle');

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

  if (modeToggle) {
    modeToggle.addEventListener('click', () => {
      const dark = body.dataset.mode === 'dark';
      body.dataset.mode = dark ? 'light' : 'dark';
      modeToggle.textContent = dark ? '暗色' : '亮色';
      const url = new URL(window.location.href);
      url.searchParams.set('mode', body.dataset.mode);
      history.replaceState(null, '', url);
    });
  }

  const initial = new URLSearchParams(window.location.search);
  if (initial.get('mode') === 'dark') {
    body.dataset.mode = 'dark';
    if (modeToggle) modeToggle.textContent = '亮色';
  }
})();
