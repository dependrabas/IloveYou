// Split the text into spans and add staggered animations
(function(){
  const line = document.querySelector('.melt-line');
  if(!line) return;
  const text = line.textContent.trim();
  line.textContent = '';

  // choose some letters to 'melt' (vowels + y)
  const meltLetters = new Set(['a','e','i','o','u','y',' ']);

  text.split('').forEach((ch, i)=>{
    const span = document.createElement('span');
    span.textContent = ch;
    const delay = i * 80;
    span.style.display = 'inline-block';
    span.style.transformOrigin = 'center bottom';
    span.style.transition = 'transform 600ms cubic-bezier(.2,.9,.2,1), opacity 400ms';
    span.style.transitionDelay = (delay/1000) + 's';

    if(meltLetters.has(ch.toLowerCase())){
      span.classList.add('drip');
    }

    line.appendChild(span);

    // appear animation
    requestAnimationFrame(()=>{
      setTimeout(()=>{
        span.style.transform = 'translateY(0)';
        span.style.opacity = '1';
      }, delay);
    });
  });

  // initial hidden state
  [...line.children].forEach(s=>{s.style.opacity='0'; s.style.transform='translateY(10px)';});

  // spawn floating hearts
  const card = document.querySelector('.card');
  const stage = document.querySelector('.stage');
  function spawnHeart(){
    const h = document.createElement('div');
    h.className = 'floating-heart float-up';
    const size = 14 + Math.random()*28;
    h.style.width = size+'px';
    h.style.height = size+'px';
    h.style.left = (20 + Math.random()*(card.clientWidth-40)) + 'px';
    h.style.bottom = '-20px';
    h.style.opacity = 0;
    h.style.background = `linear-gradient(180deg, rgba(255,92,138,0.95), rgba(255,155,179,0.95))`;
    card.appendChild(h);

    // random duration
    const dur = 3500 + Math.random()*2600;
    h.style.animationDuration = dur + 'ms';
    // small horizontal wobble using keyframes inserted inline
    const dx = (Math.random()*120-60);
    h.style.transform = `translateX(0) rotate(-45deg)`;

    // remove after animation
    setTimeout(()=>{
      h.remove();
    }, dur+200);
  }

  // spawn a few immediately staggered
  for(let i=0;i<6;i++) setTimeout(spawnHeart, i*260);
  // then intermittently
  setInterval(()=>{ spawnHeart(); }, 900);

  // sparkles
  function spawnSparkle(x,y){
    const s = document.createElement('div');
    s.className = 'sparkle';
    s.style.left = x + 'px';
    s.style.top = y + 'px';
    card.appendChild(s);
    setTimeout(()=> s.remove(), 1000);
  }

  // confetti
  function spawnConfetti(){
    const colors = ['#ff5c8a','#ff9bb3','#ffd166','#f6a6ff','#9be7ff'];
    for(let i=0;i<22;i++){
      const c = document.createElement('div');
      c.className = 'confetti';
      const w = 6 + Math.random()*16;
      c.style.width = w+'px';
      c.style.left = (10 + Math.random()*(card.clientWidth-20)) + 'px';
      c.style.top = (10 + Math.random()*40) + 'px';
      c.style.background = colors[Math.floor(Math.random()*colors.length)];
      card.appendChild(c);

      const dur = 1200 + Math.random()*1000;
      const dx = (Math.random()*200-100);
      const rot = (Math.random()*720-360);
      c.animate([
        {transform:`translateY(0px) rotate(0deg)`, opacity:1},
        {transform:`translateY(${260 + Math.random()*160}px) translateX(${dx}px) rotate(${rot}deg)`, opacity:0}
      ], {duration: dur, easing:'cubic-bezier(.2,.8,.2,1)'});

      setTimeout(()=> c.remove(), dur+200);
    }
  }

  // brief celebration on load
  setTimeout(()=>{
    spawnConfetti();
    // sprinkle sparkles across top of card
    for(let i=0;i<8;i++){
      spawnSparkle(20 + Math.random()*(card.clientWidth-40), 20 + Math.random()*20);
    }
  }, 700);

  // trigger on click/tap
  card.addEventListener('click', (ev)=>{
    const r = card.getBoundingClientRect();
    const x = ev.clientX - r.left;
    const y = ev.clientY - r.top;
    spawnSparkle(x,y);
    spawnConfetti();
  });

})();
