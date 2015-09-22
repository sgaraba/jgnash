/*
 * jGnash, a personal finance application
 * Copyright (C) 2001-2015 Craig Cavanaugh
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package jgnash.convert.imports.qif;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.ArrayList;

import jgnash.convert.imports.ImportTransaction;

/**
 * Transaction object for a QIF transaction
 *
 * @author Craig Cavanaugh
 */
@SuppressFBWarnings({"URF_UNREAD_FIELD"})
public class QifTransaction extends ImportTransaction {

    /**
     * Original date before conversion
     */
    public String oDate;

    String status = null;

    public String category = null;

    String security;
    String price;
    String quantity;
    String type;
    String amountTrans;

    public final ArrayList<QifSplitTransaction> splits = new ArrayList<>();

    public void addSplit(QifSplitTransaction split) {
        splits.add(split);
    }

    public boolean hasSplits() {
        return !splits.isEmpty();
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("Payee: ").append(payee).append('\n');
        buf.append("Memo: ").append(memo).append('\n');
        buf.append("Category: ").append(category).append('\n');
        if (amount != null) {
            buf.append("Amount:").append(amount).append('\n');
        }

        buf.append("Date: ").append(datePosted).append('\n');
        return buf.toString();
    }
}
