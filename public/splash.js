(function(){
  var MIN_MS=400,MAX_MS=2500,shown=Date.now();
  function hide(){
    var el=document.getElementById('appSplash');
    if(!el||el.dataset.hidden)return;
    el.dataset.hidden='1';
    var wait=Math.max(0,MIN_MS-(Date.now()-shown));
    setTimeout(function(){
      el.classList.add('is-hidden');
      setTimeout(function(){el.remove()},500);
    },wait);
  }
  if(document.readyState==='complete')hide();
  else window.addEventListener('load',hide);
  setTimeout(hide,MAX_MS);
})();
