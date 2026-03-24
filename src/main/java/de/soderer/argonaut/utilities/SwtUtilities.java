package de.soderer.argonaut.utilities;

import java.util.*;
import java.util.function.Function;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;

public class SwtUtilities {
	public static <T> void makeSortable(Table table, List<T> data, @SuppressWarnings("rawtypes") List<Function<T, Comparable>> valueExtractors) {
		TableColumn[] columns = table.getColumns();
		for (int i = 0; i < columns.length; i++) {
			final int columnIndex = i;
			columns[i].addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {

					TableColumn column = (TableColumn) event.widget;

					int direction = table.getSortDirection();

					if (table.getSortColumn() == column) {
						direction = direction == SWT.UP ? SWT.DOWN : SWT.UP;
					} else {
						table.setSortColumn(column);
						direction = SWT.UP;
					}

					table.setSortDirection(direction);

					Comparator<T> comparator = Comparator.comparing(valueExtractors.get(columnIndex), Comparator.nullsFirst(Comparator.naturalOrder()));

					if (direction == SWT.DOWN) {
						comparator = comparator.reversed();
					}

					data.sort(comparator);

					table.clearAll();
				}
			});
		}
	}
}
