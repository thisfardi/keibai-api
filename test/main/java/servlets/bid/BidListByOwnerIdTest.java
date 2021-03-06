package main.java.servlets.bid;

import com.google.gson.Gson;
import main.java.dao.sql.AbstractDBTest;
import main.java.dao.sql.BidDBTest;
import main.java.gson.BetterGson;
import main.java.mocks.HttpServletStubber;
import main.java.models.Bid;
import main.java.models.meta.Error;
import main.java.utils.DBFeeder;
import main.java.utils.HttpResponse;
import main.java.utils.JsonCommon;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.*;

public class BidListByOwnerIdTest extends AbstractDBTest {

    @Test
    public void test_when_user_not_authenticated() throws Exception {
        HttpServletStubber stubber = new HttpServletStubber();
        stubber.listen();
        new BidListByOwnerId().doGet(stubber.servletRequest, stubber.servletResponse);
        Error error = new BetterGson().newInstance().fromJson(stubber.gathered(), Error.class);

        assertEquals(JsonCommon.UNAUTHORIZED, error.error);
    }

    @Test
    public void test_when_user_requests_bids_that_does_not_own() throws Exception {
        HttpServletStubber stubber = new HttpServletStubber();
        stubber.authenticate(1);
        stubber.parameter("ownerid", "2").listen();
        new BidListByOwnerId().doGet(stubber.servletRequest, stubber.servletResponse);
        Error error = new BetterGson().newInstance().fromJson(stubber.gathered(), Error.class);

        assertEquals(JsonCommon.UNAUTHORIZED, error.error);
    }

    @Test
    public void test_when_owner_id_parameter_not_set() throws Exception {
        HttpServletStubber stubber = new HttpServletStubber();
        stubber.authenticate(1);
        stubber.listen();
        new BidListByOwnerId().doGet(stubber.servletRequest, stubber.servletResponse);
        Error error = new BetterGson().newInstance().fromJson(stubber.gathered(), Error.class);

        assertEquals(BidListByOwnerId.ID_NONE, error.error);
    }

    @Test
    public void test_when_owner_id_parameter_is_empty() throws Exception {
        HttpServletStubber stubber = new HttpServletStubber();
        stubber.authenticate(1);
        stubber.parameter("ownerid", "").listen();
        new BidListByOwnerId().doGet(stubber.servletRequest, stubber.servletResponse);
        Error error = new BetterGson().newInstance().fromJson(stubber.gathered(), Error.class);

        assertEquals(BidListByOwnerId.ID_INVALID, error.error);
    }

    @Test
    public void test_when_owner_id_parameter_is_NaN() throws Exception {
        HttpServletStubber stubber = new HttpServletStubber();
        stubber.authenticate(1);
        stubber.parameter("ownerid", "OMGNaN").listen();
        new BidListByOwnerId().doGet(stubber.servletRequest, stubber.servletResponse);
        Error error = new BetterGson().newInstance().fromJson(stubber.gathered(), Error.class);

        assertEquals(BidListByOwnerId.ID_INVALID, error.error);
    }

    @Test
    public void test_get_bid_list_returns_list_of_bids() throws Exception {
        Bid bid = DBFeeder.createDummyBid();
        Bid otherBid = DBFeeder.createOtherDummyBid(bid.auctionId, bid.ownerId, bid.goodId);

        List<Bid> expectedBidList = new LinkedList<Bid>() {{
            add(bid);
            add(otherBid);
        }};

        HttpServletStubber stubber = new HttpServletStubber();
        stubber.authenticate(bid.ownerId);
        stubber.parameter("ownerid", String.valueOf(bid.ownerId)).listen();
        new BidListByOwnerId().doGet(stubber.servletRequest, stubber.servletResponse);
        Bid[] modelList = new BetterGson().newInstance().fromJson(stubber.gathered(), Bid[].class);

        BidDBTest.assertBidListEquals(expectedBidList, Arrays.asList(modelList));
    }
}