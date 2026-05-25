;(function() {
    function parseProgramNames(value) {
        return value.split(/[\s,]+/).filter(function(x) { return x.length > 0; });
    }

    function startCljsGame(programNames) {
        if (window.robotwar && window.robotwar.app && window.robotwar.app.start_game) {
            window.robotwar.app.start_game(programNames);
        } else {
            console.error("CLJS start_game is not available", programNames);
        }
    }

    function init() {
        $('#programsInput').bind('keydown', function(event) {
            if (event.which === 13) {
                event.stopPropagation();
                event.preventDefault();

                $('.instruction-box').css({ height: 0 });
                setTimeout(function() {
                    $('#canvas').css({ opacity: 1 });
                }, 500);
                $(this).blur();

                startCljsGame(parseProgramNames(this.value));
            }
        });

        $.getJSON('program-names', function(data) {
            $('#programNames').text(data.names.join(", "));
            $('body').css({display: 'block'});
        });
    }

    $(init);
})();
