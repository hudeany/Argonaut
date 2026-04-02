package de.soderer.argonaut.utilities;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;

public class SwtUtilities {
	public static <T> void makeSortable(final Table table, final List<T> data, final List<Function<T, Comparable>> valueExtractors) {
		final TableColumn[] columns = table.getColumns();
		for (int i = 0; i < columns.length; i++) {
			final int columnIndex = i;
			columns[i].addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(final Event event) {

					final TableColumn column = (TableColumn) event.widget;

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
