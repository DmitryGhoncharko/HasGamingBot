package org.example;
import lombok.SneakyThrows;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.criterion.Restrictions;
import org.hibernate.internal.SessionFactoryImpl;
import org.hibernate.query.Query;
import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.MessageEntity;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import javax.swing.*;
import java.sql.Connection;
import java.util.*;

public class GamingBot extends TelegramLongPollingBot{

    private final String BOT_NAME = "BrsmGamingBot";
    private final String BOT_TOKEN = "6221029068:AAF8GY1STrEYMuIG6bO3CgwAQ69ox9rnErk";
    private static String firstCommand;
    private static String secondCommand;
    private static String JTextField = null;
    private List<List<InlineKeyboardButton>> buttons = new ArrayList<>();

    private static String actionAnswer;
    private static boolean sendGolos = true;
    private final String START_MESSAGE = "▫️Привет,это PREDICTIONS BOT \uD83E\uDD16 \n" +
            "▫️Делай предсказания на матч и за верно угаданный исход получай поинты \uD83E\uDE99\n" +
            "▫️За каждое верное предсказание получай 250\uD83E\uDE99\n" +
            "▫️Собери наибольшее количество поинтов среди других и получи приз от BRSM GAMING \uD83C\uDF81";

    private static Configuration configuration = new Configuration().addAnnotatedClass(Person.class);
    private static SessionFactory sessionFactory = configuration.buildSessionFactory();
    private static boolean isSending = true;
    private static DeleteMessage deleteMessage;
    private static String chatIdForDelete;
    private static int messageIdForDelete;

    private final List<String> adminChatIdList = new ArrayList<>(
            Arrays.asList("5964846649")
    );

    public static void main(String[] args) throws TelegramApiException{
        GamingBot bot = new GamingBot();
        TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
        telegramBotsApi.registerBot(bot);
    }

    @Override
    public String getBotUsername() {
        return BOT_NAME;
    }

    @Override
    public String getBotToken() {
        return BOT_TOKEN;
    }

    @Override
    @SneakyThrows
    public void onUpdateReceived(Update update) {
        Message message = update.getMessage();
        if(update.hasCallbackQuery()){
            handingButton(update);
            Message messageUpdate = update.getCallbackQuery().getMessage();
        } else if(adminChatIdList.contains(update.getMessage().getChatId().toString()) && isNumeric(message.getText())){
            /*
            ДОБАВИТЬ ЗАЩИТУ ОТ ГОШАНА
             */
                if(message.getText().equals("1")){
                    actionAnswer = "ORIGINAL";
                    addPoints(update);
                    sendGolos = true;
                    actionAnswer = null;
                }
                else if (message.getText().equals("2")){
                    actionAnswer = "TARGET";
                    addPoints(update);
                    sendGolos = true;
                    actionAnswer = null;
                }else if (message.getText().equals("3")){
                    sendGolos = false;
                    List<Person> list = findAllPeopleWithCriteria();
                    for (Person person : list){
                        execute(SendMessage.builder().chatId(person.getChat_id()).text("Ставочки на матч больше не принимаются!").build());
                    }
                }


        } else if(adminChatIdList.contains(update.getMessage().getChatId().toString())){
            createAdminPost(update);
        }else if(message.hasText() && message.hasEntities()){
            Optional<MessageEntity> commandEntity =
                message.getEntities().stream().filter(e -> "bot_command".equals(e.getType())).findFirst();
            if(commandEntity.isPresent()){
                String command = message.getText().substring((commandEntity.get().getOffset()), commandEntity.get().getLength());
                switch (command){
                    case "/start":
                        execute(SendMessage.builder().chatId(message.getChatId().toString()).text(START_MESSAGE).build());
                        creatingNewUser(update);
                        break;
                    case "/points":
                        execute(SendMessage.builder().
                                chatId(message.getChatId().toString()).
                                text("▫️На данный момент твой баланс составляет " + getPersonFromDataBase(update).getPoints() + " поинтов \uD83E\uDE99\n" +
                                        "\n" +
                                        "Предсказывай дальше и получай ещё больше :)").
                                build());

                        break;
                }
            }
        }
    }

    @SneakyThrows
    private void addPoints(Update update) {
        Session session = sessionFactory.openSession();
        Message message = update.getMessage();
        try {
            String chatTdFromMessage = message.getChatId().toString();
            session.beginTransaction();
            List<Person> list =
                    session.createCriteria(Person.class).add(
                            Restrictions.eq("answer", actionAnswer)
                    ).list();
            for(Person person : list){
                person.setPoints(person.getPoints()+250);
                execute(SendMessage.builder().chatId(person.getChat_id()).text("А вот и ставочка сыграла! + 250").build());
            }

            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery cq = cb.createQuery(Person.class);
            Root rootEntry = cq.from(Person.class);
            CriteriaQuery all = cq.select(rootEntry);
            TypedQuery allQuery = session.createQuery(all);
            List<Person> listForClear = allQuery.getResultList();

            for(Person person : listForClear)
                person.setAnswer(null);
            session.getTransaction().commit();
        }finally {
            session.close();
        }
    }

    public void creatingNewUser(Update update){
        Session session = sessionFactory.openSession();
        Message message = update.getMessage();
        try {
            session.beginTransaction();
            Person person = new Person(message.getChatId().toString(), 0, null);
            session.save(person);
            session.getTransaction().commit();
        }finally {
            session.close();
        }
    }

    public Person getPersonFromDataBase(Update update){
        Message message = update.getMessage();
        Session session = sessionFactory.openSession();
        try {
            String chatTdFromMessage = message.getChatId().toString();
            session.beginTransaction();
            List list =
                    session.createCriteria(Person.class).add(
                            Restrictions.eq("chat_id", chatTdFromMessage)
                    ).list();
            Person person = (Person) list.get(0);
            session.getTransaction().commit();
            return person;
        }finally {
            session.close();
        }
    }
    @SneakyThrows
    public void createAdminPost(Update update){
        Message message = update.getMessage();
        String[] splitTextFromMessage = message.getText().split("-");
        firstCommand = splitTextFromMessage[0];
        secondCommand = splitTextFromMessage[1];
        buttons.add
                (Arrays.asList(InlineKeyboardButton.builder().text(firstCommand).callbackData("ORIGINAL:").build(),
                        InlineKeyboardButton.builder().text(secondCommand).callbackData("TARGET:").build()));
        List<Person> list = findAllPeopleWithCriteria();
        for(Person person : list){
            execute(SendMessage.
                    builder().
                    chatId(person.getChat_id()).
                    replyMarkup(InlineKeyboardMarkup.builder().keyboard(buttons).build()).
                    text("   ▫️ Я всего лишь робот и не умею гадать\uD83D\uDE1E\n" +
                            "Поэтому спрошу у тебя!\uD83D\uDE0F\n" +
                            "\n" +
                            "▫️ Что ж, проверим твои пророческие способности?\uD83D\uDD2E\n" +
                            "\n" +
                            "▫️ Как ты думаешь, кто одержит победу в этом матче?\uD83E\uDD2F \n " + firstCommand + "-" + secondCommand).
                    build());
        }

        chatIdForDelete = update.getMessage().getChatId().toString();
        messageIdForDelete = update.getMessage().getMessageId();

        buttons.clear();
    }

    public List findAllPeopleWithCriteria() {
        Session session = sessionFactory.openSession();
        try {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery cq = cb.createQuery(Person.class);
            Root rootEntry = cq.from(Person.class);
            CriteriaQuery all = cq.select(rootEntry);

            TypedQuery allQuery = session.createQuery(all);
            return allQuery.getResultList();
        }finally {
            session.close();
        }
    }

    @SneakyThrows
    public void handingButton(Update update){
        Message callbackMessage = update.getCallbackQuery().getMessage();
        String[] param = update.getCallbackQuery().getData().split(":");
        String action = param[0];
        System.out.println(action);
        Session session = sessionFactory.openSession();
        try {
            String chatTdFromMessage = callbackMessage.getChatId().toString();
            session.beginTransaction();
            List list =
                    session.createCriteria(Person.class).add(
                            Restrictions.eq("chat_id", chatTdFromMessage)
                    ).list();
            Person person = (Person) list.get(0);
            if(person.getAnswer() == null && sendGolos == true){
                execute(SendMessage.builder().chatId(person.getChat_id()).text("▫️Ваше предсказание принято☑️").build());
                person.setAnswer(action);
            }else{
                execute(SendMessage.builder().chatId(person.getChat_id()).text("Жизнь не дает второго шанса").build());
            }
            session.getTransaction().commit();
        }finally {
            session.close();
        }
    }

    public static boolean isNumeric(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch(NumberFormatException e){
            return false;
        }
    }
}

/*
Configuration configuration = new Configuration().addAnnotatedClass(Person.class);
        SessionFactory sessionFactory = configuration.buildSessionFactory();
        Session session = sessionFactory.openSession();
        try
        {
            session.beginTransaction();
            String chat_id = "2";
            Query query = session.createQuery("from Person where chat_id=chat_id");
            Person person = (Person)query.stream().toArray()[0];
            session.delete(person);
            session.getTransaction().commit();
        }finally {
            session.close();
            sessionFactory.close();
        }
 */


/*
 @SneakyThrows
    public void handingButton(Update update){
        Message callbackMessage = update.getCallbackQuery().getMessage();
        String[] param = update.getCallbackQuery().getData().split(":");
        String action = param[0];
        System.out.println(action);
        Session session = sessionFactory.openSession();
        try {
            String chatTdFromMessage = callbackMessage.getChatId().toString();
            session.beginTransaction();
            List list =
                    session.createCriteria(Person.class).add(
                            Restrictions.eq("chat_id", chatTdFromMessage)
                    ).list();
            Person person = (Person) list.get(0);
            if(person.getAnswer() == null){
                person.setAnswer(action);
            }
            session.getTransaction().commit();
        }finally {
            session.close();
        }
    }
 */