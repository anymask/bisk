/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.main.portfolio.closedtrades;

import bisq.desktop.common.model.ActivatableDataModel;
import bisq.desktop.main.PriceUtil;

import bisq.core.monetary.Price;
import bisq.core.monetary.Volume;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.provider.price.MarketPrice;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.Tradable;
import bisq.core.trade.closed.ClosedTradableManager;
import bisq.core.trade.closed.ClosedTradeUtil;
import bisq.core.user.Preferences;
import bisq.core.util.VolumeUtil;

import org.bitcoinj.core.Coin;

import com.google.inject.Inject;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

class ClosedTradesDataModel extends ActivatableDataModel {

    final ClosedTradableManager closedTradableManager;
    private final ClosedTradeUtil closedTradeUtil;
    private final Preferences preferences;
    private final PriceFeedService priceFeedService;
    private final ObservableList<ClosedTradableListItem> list = FXCollections.observableArrayList();
    private final ListChangeListener<Tradable> tradesListChangeListener;

    @Inject
    public ClosedTradesDataModel(ClosedTradableManager closedTradableManager,
                                 ClosedTradeUtil closedTradeUtil,
                                 Preferences preferences,
                                 PriceFeedService priceFeedService) {
        this.closedTradableManager = closedTradableManager;
        this.closedTradeUtil = closedTradeUtil;
        this.preferences = preferences;
        this.priceFeedService = priceFeedService;

        tradesListChangeListener = change -> applyList();
    }

    @Override
    protected void activate() {
        applyList();
        closedTradableManager.getObservableList().addListener(tradesListChangeListener);
    }

    @Override
    protected void deactivate() {
        closedTradableManager.getObservableList().removeListener(tradesListChangeListener);
    }

    public ObservableList<ClosedTradableListItem> getList() {
        return list;
    }

    /**
     * Supplies a List<Tradable> from this JFX ObservableList<ClosedTradableListItem>
     * collection, for passing to core's ClosedTradeUtil which has no dependency on JFX.
     */
    public final Supplier<List<Tradable>> tradableList = () -> list.stream()
            .map(ClosedTradableListItem::getTradable)
            .collect(Collectors.toList());

    public OfferPayload.Direction getDirection(Offer offer) {
        return closedTradableManager.wasMyOffer(offer) ? offer.getDirection() : offer.getMirroredDirection();
    }

    private void applyList() {
        list.clear();

        list.addAll(closedTradableManager.getObservableList().stream().map(ClosedTradableListItem::new).collect(Collectors.toList()));

        // we sort by date, earliest first
        list.sort((o1, o2) -> o2.getTradable().getDate().compareTo(o1.getTradable().getDate()));
    }

    Coin getTotalAmount() {
        return closedTradeUtil.getTotalAmount(tradableList.get());
    }

    Map<String, Long> getTotalVolumeByCurrency() {
        return closedTradeUtil.getTotalVolumeByCurrency(tradableList.get());
    }

    public Optional<Volume> getVolumeInUserFiatCurrency(Coin amount) {
        return getVolume(amount, preferences.getPreferredTradeCurrency().getCode());
    }

    public Optional<Volume> getVolume(Coin amount, String currencyCode) {
        MarketPrice marketPrice = priceFeedService.getMarketPrice(currencyCode);
        if (marketPrice == null) {
            return Optional.empty();
        }

        // TODO Move PriceUtil & it's validators to core.util & core.util.validation
        //  before refactoring this.getVolume() to ClosedTradeUtil.
        Price price = PriceUtil.marketPriceToPrice(marketPrice);
        return Optional.of(VolumeUtil.getVolume(amount, price));
    }

    public Volume getBsqVolumeInUsdWithAveragePrice(Coin amount) {
        return closedTradeUtil.getBsqVolumeInUsdWithAveragePrice(amount);
    }

    public Coin getTotalTxFee() {
        return closedTradeUtil.getTotalTxFee(tradableList.get());
    }

    public Coin getTotalTradeFee(boolean expectBtcFee) {
        return closedTradeUtil.getTotalTradeFee(tradableList.get(), expectBtcFee);
    }
}
