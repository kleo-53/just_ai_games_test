require: slotfilling/slotFilling.sc
    module = sys.zb-common
  
require: text/text.sc
    module = sys.zb-common

require: common.js
    module = sys.zb-common
    
# Для игры Назови столицу    
# require: where/where.sc
#     module = sys.zb-common

# Импорт выше не работал, пришлось добавлять напрямую 
require: geography-ru.csv
    name = Geography
    var = $Geography

patterns:
    $Word = $entity<Geography> || converter = function ($parseTree) {
        var id = $parseTree.Geography[0].value;
        return $Geography[id].value;
        };


theme: /

    state: Start || sessionResult = "Начало игры", sessionResultColor = "#D93275"
        intent!: /sys/aimylogic/ru/hello
        q!: $regex</start>
        script:
            $jsapi.startSession();
            $session.correctAnswers = 0;
            $session.totalQuestions = 0;
        a: Добрый день! Давайте сыграем в игру «Назови столицу». Я буду называть страну, а вы попробуйте отгадать её столицу. Готовы начать?
        intent: /sys/aimylogic/ru/agreement || toState = "/AskCity"
        intent: /sys/aimylogic/ru/negation || toState = "/End"
        intent: /Продолжить || toState = "/AskCity"
        intent: /sys/aimylogic/ru/uncertainty || toState = "/AskCity"
        intent: /Начать || toState = "/AskCity"
        intent: /sys/aimylogic/ru/parting || toState = "/End"
        event: noMatch || toState = "/NoMatch"

    state: AskCity || sessionResult = "Угадай столицу", sessionResultColor = "#418614"
        script:
            var countryIds = Object.keys($Geography);
                var randomIndex = Math.floor(Math.random() * countryIds.length);
                var randomCountryId = countryIds[randomIndex];
                $session.currentCountry = $Geography[randomCountryId].value;
                if ($session.currentCountry.genCountry != "") {
            $session.countryToAsk = $session.currentCountry.genCountry 
                } else {
            $session.countryToAsk = $session.currentCountry.country 
                }
        a: Какой город является столицей {{ $session.countryToAsk }}? (Подсказка: {{ $session.currentCountry.name }})
        script:
            $session.totalQuestions++;
        intent: /sys/aimylogic/ru/uncertainty || toState = "/DontKnow"
        intent: /Хватит || toState = "/End"
        intent: /не знаю такой город || toState = "/DontKnow"
        intent: /sys/aimylogic/ru/negation || toState = "/End"
        intent: /sys/aimylogic/ru/parting || toState = "/End"
        event: noMatch || toState = "/DontKnow"

    state: NoMatch || sessionResult = "Неизвестная фраза", sessionResultColor = "#143AD1"
        a: Прошу прощения, я вас не понял. Пожалуйста, переформулируйте ваш вопрос.
        intent: /Начать || toState = "/Start"
        intent: /Хватит || toState = "/End"
        intent: /не знаю такой город || toState = "/DontKnow"
        intent: /sys/aimylogic/ru/uncertainty || toState = "/CheckAnswer"
        intent: /Продолжить || toState = "/AskCity"
        intent: /sys/aimylogic/ru/parting || toState = "/End"
        event: noMatch || toState = "/End"

    state: reset || sessionResult = "Сбросить прогресс", sessionResultColor = "#143AD1"
        intent!: /Заново
        q!: reset
        script:
            $session = {};
                $client = {};
        a: Прогресс по игре был сброшен. Начинаем заново!
        go!: /Start

    state: CheckAnswer || sessionResult = "Ответили", sessionResultColor = "#3E8080"
        q: * $Word * || fromState = "/AskCity"
        script:
            if ($parseTree._Word.name === $session.currentCountry.name) {
                $session.correctAnswers++;
            }
        if: $session.currentCountry.name === $parseTree._Word.name
            if: $session.currentCountry.complexity > 3
                random: 
                    a: Вау, это был один из самых трудных вопросов, но вы справились!
                    a: Прекрасно! Вы справились с одним из самых сложных вопросов!
                    a: Отличные знания географии!
            else: 
                random: 
                    a: Всё верно!
                    a: Правильно!
                    a: Молодец!
        else: 
            if: $session.currentCountry.complexity > 3
                a: Неверно, но не расстраивайтесь - это был один из самых сложных вопросов. На самом деле столицей {{ $session.countryToAsk}} является {{ $session.currentCountry.name }}!
            else: 
                random: 
                    a: К сожалению, вы не угадали! Столицей {{ $session.countryToAsk }} является {{ $session.currentCountry.name }}, а не {{ $parseTree._Word.name }}!
                    a: На этот раз не угадали! Столица {{ $session.countryToAsk }} - {{ $session.currentCountry.name }}, а не {{ $parseTree._Word.name }}!
        go!: /FactAndNext

    state: End || sessionResult = "Конец игры", sessionResultColor = "#D93275"
        intent!: /пока
        if: $session.correctAnswers * 2 > $session.totalQuestions
            a: Игра завершена! Вы ответили верно на большую часть вопросов: {{ $session.correctAnswers }} из {{ $session.totalQuestions }}!
        else: 
            a: Игра завершена! Вы правильно ответили на {{$session.correctAnswers}} из {{$session.totalQuestions}} вопросов.
        a: Спасибо за игру! Буду ждать вас снова!
        EndSession: 

    state: DontKnow || sessionResult = "Не ответили", sessionResultColor = "#3E8080"
        random: 
            a: Ничего страшного! Теперь вы знаете, что столица {{ $session.countryToAsk}} это {{ $session.currentCountry.name}}!
            a: Ну и ладно. На самом деле столица {{ $session.countryToAsk}} это {{ $session.currentCountry.name}}!
        go!: /FactAndNext

    state: FactAndNext || sessionResult = "Пишем факт", sessionResultColor = "#15952F"
        script:
            if ($session.totalQuestions % 5 === 0) {
                var userMessage = "Расскажи короткий интересный факт о городе " + $session.currentCountry.name + " или стране " + $session.countryToAsk;
                try {
            var assistantResponse = $gpt.createChatCompletion([{ "role": "user", "content": userMessage }]);
            var response = assistantResponse.choices[0].message.content;
            $reactions.answer(response);
                } catch (error) {
                }
            }
        a: Играем дальше?
        intent: /sys/aimylogic/ru/agreement || toState = "/AskCity"
        intent: /sys/aimylogic/ru/negation || toState = "/End"
        intent: /sys/aimylogic/ru/uncertainty || toState = "/AskCity"
        intent: /Хватит || toState = "/End"
        intent: /Продолжить || toState = "/AskCity"
        intent: /sys/aimylogic/ru/parting || toState = "/End"
        event: noMatch || toState = "/NoMatch"

    # state: OnError
    #     script:
    #         bind("onAnyError", function($context) {
    #             var answers = [
    #             "Что-то пошло не так.",
    #             "Произошла ошибка. Пожалуйста, повторите запрос позже.",
    #             "Все сломалось. Попробуйте еще раз."
    #         ];
    #         var randomAnswer = answers[$reactions.random(answers.length)];
    #         $reactions.answer(randomAnswer);
    #     });
    #     EndSession: 