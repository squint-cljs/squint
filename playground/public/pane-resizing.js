// The existing divider sits inside the sidebar; #main only needs two grid
// columns. CSS clamps the percentage split to keep both panes usable.
const main = document.querySelector('#main');
const editorPane = document.querySelector('#editor');
const divider = document.querySelector('#draggable-divider');
const desktop = matchMedia('(min-width: 768px)');
const storageKey = 'playground.pane-split';
let pointerId = null;
let grabOffset = 0;

function updateAria() {
  if (!desktop.matches) return;
  const width = main.getBoundingClientRect().width;
  const minimum =
    (parseFloat(getComputedStyle(main).getPropertyValue('--pane-min-width')) /
      width) *
    100;
  const percentage = (editorPane.getBoundingClientRect().width / width) * 100;
  divider.setAttribute('aria-valuemin', String(Math.round(minimum)));
  divider.setAttribute('aria-valuemax', String(Math.round(100 - minimum)));
  divider.setAttribute('aria-valuenow', String(Math.round(percentage)));
  divider.setAttribute(
    'aria-valuetext',
    `${Math.round(percentage)}% editor, ${Math.round(
      100 - percentage
    )}% sidebar`
  );
}

function setSplit(percentage) {
  main.style.setProperty(
    '--pane-split',
    `${Math.min(100, Math.max(0, percentage))}%`
  );
  updateAria();
}

function saveSplit() {
  try {
    const percentage = parseFloat(
      getComputedStyle(main).getPropertyValue('--pane-split')
    );
    localStorage.setItem(
      storageKey,
      String(Number.isFinite(percentage) ? percentage : 50)
    );
  } catch (_) {
    /* Resizing still works when storage is disabled. */
  }
}

function endDrag(event) {
  if (pointerId === null) return;
  if (event?.pointerId !== undefined && event.pointerId !== pointerId) return;
  const capturedPointer = pointerId;
  pointerId = null;
  if (divider.hasPointerCapture(capturedPointer))
    divider.releasePointerCapture(capturedPointer);
  main.classList.remove('pane-resizing');
  divider.classList.remove('pane-resizing', 'pointer-over');
  saveSplit();
}

divider.addEventListener('pointerdown', (event) => {
  if (
    !desktop.matches ||
    event.button !== 0 ||
    !event.isPrimary ||
    pointerId !== null
  )
    return;
  grabOffset = event.clientX - editorPane.getBoundingClientRect().right;
  pointerId = event.pointerId;
  divider.setPointerCapture(pointerId);
  main.classList.add('pane-resizing');
  divider.classList.add('pane-resizing', 'pointer-over', 'pointer-focused');
});

divider.addEventListener('pointermove', (event) => {
  if (
    event.pointerId !== pointerId ||
    !divider.hasPointerCapture(event.pointerId)
  )
    return;
  const bounds = main.getBoundingClientRect();
  setSplit(((event.clientX - bounds.left - grabOffset) / bounds.width) * 100);
  // Pointer capture keeps :hover active outside the handle. Paint its hover
  // gradient only while the pointer is physically inside the moving hit area.
  const hitArea = divider.getBoundingClientRect();
  divider.classList.toggle(
    'pointer-over',
    event.clientX >= hitArea.left &&
      event.clientX < hitArea.right &&
      event.clientY >= hitArea.top &&
      event.clientY < hitArea.bottom
  );
});

divider.addEventListener('pointerup', endDrag);
divider.addEventListener('pointercancel', endDrag);
divider.addEventListener('lostpointercapture', endDrag);
window.addEventListener('blur', endDrag);
divider.addEventListener('blur', () =>
  divider.classList.remove('pointer-focused')
);

divider.addEventListener('keydown', (event) => {
  divider.classList.remove('pointer-focused');
  if (!desktop.matches) return;
  const bounds = main.getBoundingClientRect();
  const current =
    (editorPane.getBoundingClientRect().width / bounds.width) * 100;
  const step = event.shiftKey ? 10 : 2;
  let next;
  switch (event.key) {
    case 'ArrowLeft':
      next = current - step;
      break;
    case 'ArrowRight':
      next = current + step;
      break;
    case 'Home':
      next = 0;
      break;
    case 'End':
      next = 100;
      break;
    default:
      return;
  }
  event.preventDefault();
  setSplit(next);
  saveSplit();
});

divider.addEventListener('dblclick', () => {
  if (!desktop.matches) return;
  setSplit(50);
  saveSplit();
});

desktop.addEventListener('change', () => {
  endDrag();
  updateAria();
});
const resizeObserver = new ResizeObserver(updateAria);
resizeObserver.observe(main);
resizeObserver.observe(editorPane);
updateAria();
