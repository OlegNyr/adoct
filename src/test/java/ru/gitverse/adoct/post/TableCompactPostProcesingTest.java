package ru.gitverse.adoct.post;

import org.junit.Test;

import static org.junit.Assert.*;

public class TableCompactPostProcesingTest {
    @Test
    public void name() {
        String str = """
                
                [cols="1a,1a,1a"]
                |===
                |Контакт|Роль в процессе|Область ответственности
                
                |
                link:https://confluence.example.com/display/~16675799[Борисовский Илья Евгеньевич]\s
                
                |Автор документа
                |Архитектор сервиса/Аналитик
                
                |
                
                |Автор документа
                |Аналитик
                
                |
                link:https://confluence.example.com/display/~16959190[Шадрин Михаил Дмитриевич]\s
                
                |Участник
                |Разработчик(и)
                
                |
                link:https://confluence.example.com/display/~21200757[Кулаков Владислав Валерьевич]\s
                
                link:https://confluence.example.com/display/~20950501[Unknown User (20950501)]\s
                
                link:https://confluence.example.com/display/~21279638[Голенков Сергей Юрьевич]\s
                
                |Участник
                |Тестировщик(и)
                
                |
                link:https://confluence.example.com/display/~17540382[Горобец Дмитрий Дмитриевич]\s
                
                |Согласующий
                |ЭПО Сопровождение канальное
                
                |
                link:https://confluence.example.com/display/~20173559[Кулагин Алексей Станиславович]\s
                
                |Согласующий
                |ЭПО Сопровождение продуктовое
                
                |
                
                |Участник
                |Главный прикладной архитектор сегмента ФЛ
                
                |
                link:https://confluence.example.com/display/~22140629[Милорадов Владимир Андреевич]\s
                
                |Согласующий
                |Эксперт кибербезопасности
                
                |
                link:https://confluence.example.com/display/~17395544[Нырков Олег Александрович]\s
                
                |Участник
                |Архитектор КА ФО
                
                |
                link:https://confluence.example.com/display/~Popov-IY[Попов Игорь Юрьевич]\s
                
                |Участник
                |Владелец продукта
                
                |
                link:https://confluence.example.com/display/~17172950[Барсуков Андрей Анатольевич]\s
                
                |Участник
                |Архитектор трайба
                
                |===
                """;

        TableCompactPostProcesing t = new TableCompactPostProcesing();
        System.out.println(t.execute(str));
    }
}