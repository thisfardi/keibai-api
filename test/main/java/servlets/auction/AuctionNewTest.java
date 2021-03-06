package main.java.servlets.auction;

import com.google.gson.Gson;
import main.java.dao.DAOException;
import main.java.dao.EventDAO;
import main.java.dao.sql.AbstractDBTest;
import main.java.dao.sql.EventDAOSQL;
import main.java.gson.BetterGson;
import main.java.mocks.HttpServletStubber;
import main.java.models.Auction;
import main.java.models.Event;
import main.java.models.User;
import main.java.models.meta.Error;
import main.java.utils.DBFeeder;
import main.java.utils.DummyGenerator;
import main.java.utils.ImpreciseDate;
import org.junit.Test;

import javax.servlet.ServletException;
import java.io.IOException;

import static org.junit.Assert.*;

public class AuctionNewTest extends AbstractDBTest {

    @Test
    public void test_not_authenticate_user_can_not_create_auction() throws DAOException, IOException, ServletException {
        HttpServletStubber stubber = new HttpServletStubber();

        Auction attemptAuction = DummyGenerator.getDummyAuction();
        String attemptAuctionJson = new BetterGson().newInstance().toJson(attemptAuction);
        stubber.body(attemptAuctionJson).listen();
        new AuctionNew().doPost(stubber.servletRequest, stubber.servletResponse);

        Error error = new BetterGson().newInstance().fromJson(stubber.gathered(), Error.class);
        assertEquals("Unauthorized.", error.error);
    }

    @Test
    public void test_auction_with_blank_name_can_not_be_created() throws DAOException, IOException, ServletException {
        Auction attemptAuction = DummyGenerator.getDummyAuction();
        attemptAuction.name = "";
        common_auction_error_test(attemptAuction, AuctionNew.NAME_ERROR);
    }

    @Test
    public void test_english_auction_with_non_positive_starting_price_can_not_be_created() throws ServletException, DAOException, IOException {
        EventDAO eventDAO = EventDAOSQL.getInstance();

        Event dummyEvent = DBFeeder.createDummyEvent();
        dummyEvent.status = Event.OPENED;
        dummyEvent.auctionType = Event.ENGLISH;
        Event event = eventDAO.update(dummyEvent);

        Auction attemptAuction = DummyGenerator.getDummyAuction();
        attemptAuction.eventId = event.id;
        attemptAuction.startTime = null;
        attemptAuction.startingPrice = 0;
        common_auction_error_test(attemptAuction, AuctionNew.AUCTION_STARTING_PRICE_ERROR);
    }

    @Test
    public void auction_should_be_given_pending_status() throws ServletException, DAOException, IOException {
        User dummyUser = DBFeeder.createDummyUser();
        Event dummyEvent = DBFeeder.createDummyEvent();

        Auction attemptAuction = DummyGenerator.getDummyAuction();
        attemptAuction.ownerId = dummyUser.id;
        attemptAuction.eventId = dummyEvent.id;
        attemptAuction.startTime = null;
        attemptAuction.status = Auction.IN_PROGRESS; // Hack attempt.
        String attemptAuctionJson = new BetterGson().newInstance().toJson(attemptAuction);

        HttpServletStubber stubber = new HttpServletStubber();
        stubber.authenticate(dummyUser.id);
        stubber.body(attemptAuctionJson).listen();
        new AuctionNew().doPost(stubber.servletRequest, stubber.servletResponse);

        Auction outputAuction = new BetterGson().newInstance().fromJson(stubber.gathered(), Auction.class);

        assertEquals(Auction.PENDING, outputAuction.status);
    }

    @Test
    public void should_create_new_auction() throws IOException, ServletException, DAOException {
        User dummyUser = DBFeeder.createDummyUser();
        Event dummyEvent = DBFeeder.createDummyEvent();

        Auction attemptAuction = DummyGenerator.getDummyAuction();
        attemptAuction.eventId = dummyEvent.id;
        attemptAuction.startTime = null;
        String attemptAuctionJson = new BetterGson().newInstance().toJson(attemptAuction);

        HttpServletStubber stubber = new HttpServletStubber();
        stubber.authenticate(dummyUser.id);
        stubber.body(attemptAuctionJson).listen();
        new AuctionNew().doPost(stubber.servletRequest, stubber.servletResponse);

        Auction outputAuction = new BetterGson().newInstance().fromJson(stubber.gathered(), Auction.class);

        assertEquals(attemptAuction.name, outputAuction.name);
        assertEquals(attemptAuction.startingPrice, outputAuction.startingPrice, 0.01);
        assertEquals(attemptAuction.startTime, outputAuction.startTime);
        assertEquals(dummyUser.id, outputAuction.ownerId);
        assertEquals(Auction.PENDING, outputAuction.status);
    }

    private void common_auction_error_test(Auction attemptAuction, String errorMsg) throws DAOException, IOException, ServletException {
        User dummyUser = DBFeeder.createDummyUser();

        attemptAuction.ownerId = dummyUser.id;
        String attemptAuctionJson = new BetterGson().newInstance().toJson(attemptAuction);

        HttpServletStubber stubber = new HttpServletStubber();
        stubber.authenticate(dummyUser.id);
        stubber.body(attemptAuctionJson).listen();
        new AuctionNew().doPost(stubber.servletRequest, stubber.servletResponse);

        Error error = new BetterGson().newInstance().fromJson(stubber.gathered(), Error.class);
        assertEquals(errorMsg, error.error);
    }

    @Test
    public void does_not_throw_event_does_not_exist_if_event_does_exist() throws Exception {
        User dummyUser = DBFeeder.createDummyUser();

        EventDAO eventDAO = EventDAOSQL.getInstance();
        Event dummyEvent = DummyGenerator.getDummyEvent();
        dummyEvent.ownerId = dummyUser.id;
        Event dbEvent = eventDAO.create(dummyEvent);

        Auction bodyAuction = DummyGenerator.getDummyAuction();
        bodyAuction.eventId = dbEvent.id;

        HttpServletStubber stubber = new HttpServletStubber();
        stubber.body(new BetterGson().newInstance().toJson(bodyAuction)).authenticate(dummyUser.id).listen();
        new AuctionNew().doPost(stubber.servletRequest, stubber.servletResponse);
        Auction outputAuction = new BetterGson().newInstance().fromJson(stubber.gathered(), Auction.class);

        assertEquals(bodyAuction.name, outputAuction.name);
        assertEquals(bodyAuction.startingPrice, outputAuction.startingPrice, 0.01);
        assertNull(outputAuction.startTime); // Start time shouldn't be set by user.
        assertNotEquals(outputAuction.ownerId, 0);
        assertEquals(bodyAuction.status, outputAuction.status);
        assertEquals(bodyAuction.eventId, outputAuction.eventId);
    }

    @Test
    public void does_throw_event_does_not_exist_if_event_does_not_exist() throws Exception {
        User dummyUser = DBFeeder.createDummyUser();

        Auction bodyAuction = DummyGenerator.getDummyAuction();
        bodyAuction.eventId = 1;

        HttpServletStubber stubber = new HttpServletStubber();
        stubber.body(new BetterGson().newInstance().toJson(bodyAuction)).authenticate(dummyUser.id).listen();
        new AuctionNew().doPost(stubber.servletRequest, stubber.servletResponse);

        Error error = new BetterGson().newInstance().fromJson(stubber.gathered(), Error.class);
        assertEquals(AuctionNew.EVENT_NOT_EXIST_ERROR, error.error);
    }

    @Test
    public void throw_event_closed_when_event_not_opened() throws Exception {
        EventDAO eventDAO = EventDAOSQL.getInstance();

        Event dummyEvent = DBFeeder.createDummyEvent();
        dummyEvent.status = Event.IN_PROGRESS;
        Event event = eventDAO.update(dummyEvent);

        Auction bodyAuction = DummyGenerator.getDummyAuction();
        bodyAuction.eventId = event.id;
        bodyAuction.ownerId = event.ownerId;

        HttpServletStubber stubber = new HttpServletStubber();
        stubber.body(new BetterGson().newInstance().toJson(bodyAuction)).authenticate(event.ownerId).listen();
        new AuctionNew().doPost(stubber.servletRequest, stubber.servletResponse);

        Error error = new BetterGson().newInstance().fromJson(stubber.gathered(), Error.class);
        assertEquals(AuctionNew.EVENT_NOT_OPENED, error.error);
    }

    @Test
    public void combinatorial_auction_without_price_is_created() throws Exception {
        EventDAO eventDAO = EventDAOSQL.getInstance();

        Event dummyEvent = DBFeeder.createDummyEvent();
        dummyEvent.status = Event.OPENED;
        dummyEvent.auctionType = Event.COMBINATORIAL;
        Event event = eventDAO.update(dummyEvent);

        Auction attemptAuction = DummyGenerator.getDummyAuction();
        attemptAuction.eventId = dummyEvent.id;
        attemptAuction.startTime = null;
        String attemptAuctionJson = new BetterGson().newInstance().toJson(attemptAuction);

        HttpServletStubber stubber = new HttpServletStubber();
        stubber.authenticate(event.ownerId);
        stubber.body(attemptAuctionJson).listen();
        new AuctionNew().doPost(stubber.servletRequest, stubber.servletResponse);

        Auction outputAuction = new BetterGson().newInstance().fromJson(stubber.gathered(), Auction.class);

        assertEquals(attemptAuction.name, outputAuction.name);
        assertEquals(1.0, outputAuction.startingPrice, 0.01);
        assertEquals(attemptAuction.startTime, outputAuction.startTime);
        assertEquals(event.ownerId, outputAuction.ownerId);
        assertEquals(Auction.PENDING, outputAuction.status);
    }

    @Test
    public void combinatorial_auction_price_is_always_1() throws Exception {
        EventDAO eventDAO = EventDAOSQL.getInstance();

        Event dummyEvent = DBFeeder.createDummyEvent();
        dummyEvent.status = Event.OPENED;
        dummyEvent.auctionType = Event.COMBINATORIAL;
        Event event = eventDAO.update(dummyEvent);

        Auction attemptAuction = DummyGenerator.getDummyAuction();
        attemptAuction.eventId = dummyEvent.id;
        attemptAuction.startTime = null;
        attemptAuction.startingPrice = 10000;
        String attemptAuctionJson = new BetterGson().newInstance().toJson(attemptAuction);

        HttpServletStubber stubber = new HttpServletStubber();
        stubber.authenticate(event.ownerId);
        stubber.body(attemptAuctionJson).listen();
        new AuctionNew().doPost(stubber.servletRequest, stubber.servletResponse);

        Auction outputAuction = new BetterGson().newInstance().fromJson(stubber.gathered(), Auction.class);

        assertEquals(attemptAuction.name, outputAuction.name);
        assertEquals(1.0, outputAuction.startingPrice, 0.01);
        assertEquals(attemptAuction.startTime, outputAuction.startTime);
        assertEquals(event.ownerId, outputAuction.ownerId);
        assertEquals(Auction.PENDING, outputAuction.status);
    }

}