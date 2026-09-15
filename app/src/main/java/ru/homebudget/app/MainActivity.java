package ru.homebudget.app;

import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.math.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Offline expense tracker. Monetary values are stored as integer kopecks. */
public class MainActivity extends Activity {
    private static final int BG = 0xFFF5F6F0, INK = 0xFF20382F, MUTED = 0xFF708077;
    private static final int GREEN = 0xFF28664D, PALE = 0xFFE6EBDD, RED = 0xFFB5443C;
    private static final Locale RU = new Locale("ru", "RU");
    private final ArrayList<Expense> expenses = new ArrayList<>();
    private final ArrayList<String> categories = new ArrayList<>();
    private final HashMap<String, Long> budgets = new HashMap<>();
    private YearMonth month = YearMonth.now();
    private LinearLayout root, content;
    private String page = "Обзор", filter = "Все категории";
    private String pendingCsv;
    private boolean storageHealthy = true;

    private static class Expense {
        String id, category, note;
        LocalDate date;
        long amount;
        Expense(String id, long amount, String category, String note, LocalDate date) {
            this.id = id; this.amount = amount; this.category = category;
            this.note = note; this.date = date;
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        load();
        if (state != null) {
            month = YearMonth.parse(state.getString("month", month.toString()));
            page = state.getString("page", "Обзор");
            filter = state.getString("filter", "Все категории");
            pendingCsv = state.getString("csv");
        }
        render();
        if (!storageHealthy) new AlertDialog.Builder(this).setTitle("Не удалось прочитать данные")
            .setMessage("Сохранённые данные не изменены. Запись отключена, чтобы избежать потери истории.")
            .setPositiveButton("Понятно", null).show();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("month", month.toString()); out.putString("page", page);
        out.putString("filter", filter); out.putString("csv", pendingCsv);
        super.onSaveInstanceState(out);
    }

    private void load() {
        categories.clear(); expenses.clear(); budgets.clear();
        String raw = getSharedPreferences("budget", MODE_PRIVATE).getString("data", null);
        if (raw == null) {
            categories.addAll(Arrays.asList("Продукты", "Транспорт", "Дом", "Кафе", "Здоровье", "Покупки", "Развлечения", "Другое"));
            return;
        }
        try {
            JSONObject data = new JSONObject(raw);
            JSONArray cs = data.getJSONArray("categories");
            for (int i=0; i<cs.length(); i++) categories.add(cs.getString(i));
            JSONObject bs = data.getJSONObject("budgets");
            Iterator<String> keys = bs.keys();
            while (keys.hasNext()) { String key = keys.next(); budgets.put(key, bs.getLong(key)); }
            JSONArray es = data.getJSONArray("expenses");
            for (int i=0; i<es.length(); i++) {
                JSONObject e = es.getJSONObject(i);
                expenses.add(new Expense(e.getString("id"), e.getLong("amount"), e.getString("category"),
                    e.getString("note"), LocalDate.parse(e.getString("date"))));
            }
        } catch (Exception e) { storageHealthy = false; }
    }

    private boolean save() {
        if (!storageHealthy) { toast("Запись отключена: ошибка чтения данных"); return false; }
        try {
            JSONArray es = new JSONArray();
            for (Expense e : expenses) es.put(new JSONObject().put("id", e.id).put("amount", e.amount)
                .put("category", e.category).put("note", e.note).put("date", e.date.toString()));
            JSONObject data = new JSONObject().put("version", 1).put("categories", new JSONArray(categories))
                .put("budgets", new JSONObject(budgets)).put("expenses", es);
            if (!getSharedPreferences("budget", MODE_PRIVATE).edit().putString("data", data.toString()).commit())
                throw new IOException("Storage failure");
            return true;
        } catch (Exception e) { toast("Не удалось сохранить. Попробуйте ещё раз."); return false; }
    }

    private void render() {
        root = column(); root.setBackgroundColor(BG);
        root.setPadding(dp(20), dp(8), dp(20), dp(10));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(dp(20), dp(8)+insets.getSystemWindowInsetTop(), dp(20), dp(10)+insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(root); root.requestApplyInsets();
        TextView brand = text("ДОМАШНИЙ БЮДЖЕТ", 12, GREEN); brand.setLetterSpacing(.16f); root.addView(brand);
        TextView title = text(page.equals("Обзор") ? "Деньги под контролем" : page, 27, INK);
        title.setTypeface(null, Typeface.BOLD); root.addView(title); space(root, 14);
        LinearLayout months = row();
        Button prev = button("‹", () -> { month = month.minusMonths(1); render(); });
        prev.setContentDescription("Предыдущий месяц"); months.addView(prev, new LinearLayout.LayoutParams(dp(52), dp(48)));
        String label = month.format(DateTimeFormatter.ofPattern("LLLL yyyy", RU));
        TextView heading = text(label.substring(0,1).toUpperCase(RU)+label.substring(1), 17, INK);
        heading.setGravity(Gravity.CENTER); months.addView(heading, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button next = button("›", () -> { month = month.plusMonths(1); render(); });
        next.setContentDescription("Следующий месяц"); months.addView(next, new LinearLayout.LayoutParams(dp(52), dp(48)));
        root.addView(months); space(root, 12);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        content = column(); scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        if (page.equals("Обзор")) overview(); else if (page.equals("История")) history(); else categoryPage();
        space(root, 8);
        Button add = button("+  Добавить расход", () -> editExpense(null));
        add.setBackground(shape(GREEN)); add.setTextColor(Color.WHITE); root.addView(add, new LinearLayout.LayoutParams(-1, dp(54)));
        space(root, 8);
        LinearLayout nav = row();
        for (String name : new String[]{"Обзор", "История", "Категории"}) {
            Button b = button(name, () -> { page = name; render(); });
            b.setTextSize(12); b.setBackground(shape(page.equals(name) ? PALE : BG));
            nav.addView(b, new LinearLayout.LayoutParams(0, dp(48), 1));
        }
        root.addView(nav);
    }

    private List<Expense> current() {
        ArrayList<Expense> list = new ArrayList<>();
        for (Expense e : expenses) if (YearMonth.from(e.date).equals(month)) list.add(e);
        list.sort((a,b) -> b.date.compareTo(a.date)); return list;
    }
    private long total(List<Expense> list) { long sum=0; for (Expense e : list) sum+=e.amount; return sum; }

    private void overview() {
        List<Expense> list = current(); long spent = total(list), limit = budgets.getOrDefault(month.toString(), 0L);
        LinearLayout hero = card(); hero.setBackground(shape(GREEN));
        hero.addView(text("РАСХОДЫ ЗА МЕСЯЦ", 12, 0xFFD4E5D9));
        TextView sum = text(money(spent), 34, Color.WHITE); sum.setTypeface(null, Typeface.BOLD); hero.addView(sum);
        space(hero, 12);
        if (limit > 0) {
            hero.addView(text((spent > limit ? "Превышение: " : "Осталось: ")+money(Math.abs(limit-spent)), 16, Color.WHITE));
            space(hero, 8); progress(hero, spent, limit, 0xFFCCE3A5);
            hero.addView(text("Лимит на месяц · "+money(limit), 13, 0xFFD4E5D9));
        } else hero.addView(text("Задайте лимит, чтобы следить за остатком", 14, Color.WHITE));
        Button budget = button(limit > 0 ? "Изменить лимит" : "Установить бюджет", this::editBudget);
        hero.addView(budget); content.addView(hero); space(content, 20);
        section("По категориям");
        if (list.isEmpty()) empty("Здесь появится ваша сводка", "Добавьте первый расход — приложение посчитает траты по категориям.");
        else {
            Map<String, Long> sums = new HashMap<>();
            for (Expense e : list) sums.put(e.category, sums.getOrDefault(e.category,0L)+e.amount);
            ArrayList<Map.Entry<String,Long>> sorted = new ArrayList<>(sums.entrySet());
            sorted.sort((a,b) -> Long.compare(b.getValue(),a.getValue()));
            for (Map.Entry<String,Long> entry : sorted) {
                LinearLayout c = card();
                c.addView(text(entry.getKey()+"   ·   "+money(entry.getValue()), 16, INK));
                space(c, 8); progress(c,entry.getValue(),spent,GREEN);
                c.addView(text(String.format(RU,"%.1f %% от расходов",entry.getValue()*100.0/spent),12,MUTED));
                c.setOnClickListener(v -> { filter=entry.getKey(); page="История"; render(); });
                content.addView(c); space(content,8);
            }
            space(content,12); section("Последние расходы");
            for (int i=0;i<Math.min(3,list.size());i++) expenseRow(list.get(i));
        }
    }

    private void history() {
        ArrayList<String> options = new ArrayList<>(); options.add("Все категории"); options.addAll(categories);
        Spinner selector = spinner(options); selector.setSelection(Math.max(0,options.indexOf(filter)));
        content.addView(selector, new LinearLayout.LayoutParams(-1,dp(52)));
        selector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> parent) {}
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = options.get(position);
                if (!filter.equals(selected)) { filter = selected; render(); }
            }
        });
        ArrayList<Expense> list = new ArrayList<>();
        for (Expense e : current()) if (filter.equals("Все категории") || filter.equals(e.category)) list.add(e);
        section("Всего: "+money(total(list))); content.addView(text("Записей: "+list.size()+" · Нажмите запись для изменения",12,MUTED));
        space(content,12);
        if (list.isEmpty()) empty("Расходов пока нет", "Выберите другой месяц или добавьте расход.");
        for (Expense e : list) expenseRow(e);
        space(content,12); content.addView(button("Экспорт этого списка в CSV", () -> export(list)));
    }

    private void categoryPage() {
        content.addView(text("Свои категории помогают понять, куда уходят деньги.",15,MUTED)); space(content,16);
        for (String category : categories) {
            LinearLayout c = card(); c.addView(text(category,18,INK)); content.addView(c); space(content,8);
        }
        content.addView(button("+  Новая категория", () -> {
            EditText name = input("Например, питомцы", false); LinearLayout box = dialogBox(); box.addView(name);
            AlertDialog d = new AlertDialog.Builder(this).setTitle("Новая категория").setView(box)
                .setNegativeButton("Отмена",null).setPositiveButton("Добавить",null).create();
            d.setOnShowListener(v -> d.getButton(-1).setOnClickListener(w -> {
                String value = name.getText().toString().trim();
                if (value.isEmpty() || value.length()>40) { name.setError("Введите от 1 до 40 символов"); return; }
                for (String c : categories) if (c.equalsIgnoreCase(value)) { name.setError("Такая категория уже есть"); return; }
                if (value.equalsIgnoreCase("Все категории")) { name.setError("Выберите другое название"); return; }
                categories.add(value);
                if (save()) { d.dismiss(); render(); } else categories.remove(value);
            })); d.show();
        }));
    }

    private void expenseRow(Expense e) {
        LinearLayout card = card();
        TextView top = text(e.category+"   ·   "+money(e.amount),17,INK); top.setTypeface(null,Typeface.BOLD); card.addView(top);
        card.addView(text(e.date.format(DateTimeFormatter.ofPattern("d MMMM",RU))+(e.note.isEmpty()?"":" · "+e.note),13,MUTED));
        card.setOnClickListener(v -> editExpense(e)); content.addView(card); space(content,8);
    }

    private void editExpense(Expense old) {
        if (!storageHealthy || categories.isEmpty()) { toast("Данные недоступны для записи"); return; }
        LinearLayout box = dialogBox();
        box.addView(text("Сумма, ₽",13,MUTED)); EditText amount = input("0,00",true); box.addView(amount);
        box.addView(text("Категория",13,MUTED)); Spinner category = spinner(categories); box.addView(category);
        box.addView(text("Комментарий",13,MUTED)); EditText note = input("Необязательно",false); box.addView(note);
        LocalDate[] date = {old == null ? (month.equals(YearMonth.now()) ? LocalDate.now() : month.atDay(1)) : old.date};
        Button dateButton = button("", () -> {});
        Runnable label = () -> dateButton.setText(date[0].format(DateTimeFormatter.ofPattern("d MMMM yyyy",RU)));
        label.run(); dateButton.setOnClickListener(v -> new DatePickerDialog(this,(picker,y,m,d) -> {
            date[0]=LocalDate.of(y,m+1,d); label.run();
        },date[0].getYear(),date[0].getMonthValue()-1,date[0].getDayOfMonth()).show());
        box.addView(text("Дата",13,MUTED)); box.addView(dateButton);
        if (old != null) { amount.setText(BigDecimal.valueOf(old.amount,2).toPlainString()); note.setText(old.note); category.setSelection(categories.indexOf(old.category)); }
        ScrollView scroll = new ScrollView(this); scroll.addView(box);
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle(old == null ? "Новый расход" : "Изменить расход")
            .setView(scroll).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null);
        if (old != null) builder.setNeutralButton("Удалить",(dialog,which) -> confirmDelete(old));
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(v -> dialog.getButton(-1).setOnClickListener(w -> {
            long value;
            try { value = parseMoney(amount.getText().toString(),false); }
            catch (Exception e) { amount.setError("Введите сумму от 0,01 до 999 999 999,99 (до 2 знаков после запятой)"); return; }
            String comment = note.getText().toString().trim();
            if (comment.length()>200) { note.setError("Максимум 200 символов"); return; }
            Expense entry = new Expense(old == null ? UUID.randomUUID().toString() : old.id,value,
                categories.get(category.getSelectedItemPosition()),comment,date[0]);
            int index = old == null ? -1 : expenses.indexOf(old);
            if (index>=0) expenses.set(index,entry); else expenses.add(entry);
            if (save()) { month=YearMonth.from(date[0]); dialog.dismiss(); render(); }
            else { if(index>=0) expenses.set(index,old); else expenses.remove(entry); }
        })); dialog.show();
    }

    private void confirmDelete(Expense e) {
        new AlertDialog.Builder(this).setTitle("Удалить расход?").setMessage(e.category+" · "+money(e.amount))
            .setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w) -> {
                int index=expenses.indexOf(e); expenses.remove(e);
                if(save()) render(); else expenses.add(index,e);
            }).show();
    }

    private void editBudget() {
        LinearLayout box = dialogBox(); box.addView(text("Месячный лимит, ₽. Укажите 0, чтобы убрать лимит.",14,MUTED));
        EditText amount = input("Например, 50000",true); box.addView(amount);
        Long old=budgets.get(month.toString()); if(old!=null) amount.setText(BigDecimal.valueOf(old,2).toPlainString());
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Бюджет на месяц").setView(box)
            .setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null).create();
        dialog.setOnShowListener(v -> dialog.getButton(-1).setOnClickListener(w -> {
            try {
                long value=parseMoney(amount.getText().toString(),true); budgets.put(month.toString(),value);
                if(save()) { dialog.dismiss(); render(); }
                else if(old==null) budgets.remove(month.toString()); else budgets.put(month.toString(),old);
            } catch(Exception e) { amount.setError("Введите сумму от 0 до 999 999 999,99"); }
        })); dialog.show();
    }

    static long parseMoney(String text, boolean allowZero) {
        String clean=text.trim().replace(" ","").replace("\u00a0","").replace(',','.');
        if(!clean.matches("[0-9]+(\\.[0-9]{1,2})?")) throw new IllegalArgumentException();
        long value=new BigDecimal(clean).movePointRight(2).longValueExact();
        if(value<(allowZero?0:1) || value>99999999999L) throw new IllegalArgumentException();
        return value;
    }

    private void export(List<Expense> list) {
        if(list.isEmpty()) { toast("Нет расходов для экспорта"); return; }
        StringBuilder csv=new StringBuilder("\uFEFFДата;Категория;Комментарий;Сумма (RUB)\r\n");
        for(Expense e:list) csv.append(e.date).append(';').append(csvCell(e.category)).append(';')
            .append(csvCell(e.note)).append(';').append(BigDecimal.valueOf(e.amount,2).toPlainString().replace('.',',')).append("\r\n");
        pendingCsv=csv.toString();
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
            .setType("text/csv").putExtra(Intent.EXTRA_TITLE,"expenses-"+month+".csv");
        try { startActivityForResult(intent,10); } catch(ActivityNotFoundException e) { toast("Нет приложения для сохранения файла"); }
    }
    private String csvCell(String s) {
        if(s.matches("^[\\s]*[=+@-].*")) s="'"+s;
        return "\""+s.replace("\"","\"\"")+"\"";
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==10 && result==RESULT_OK && data!=null && data.getData()!=null && pendingCsv!=null) {
            try(OutputStream out=getContentResolver().openOutputStream(data.getData())) {
                if(out==null) throw new IOException();
                out.write(pendingCsv.getBytes(StandardCharsets.UTF_8)); toast("CSV сохранён");
            } catch(Exception e) { toast("Не удалось сохранить CSV"); }
        }
        if(request==10) pendingCsv=null;
    }

    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    private void toast(String message) { Toast.makeText(this,message,Toast.LENGTH_LONG).show(); }
    private String money(long value) { return String.format(RU,"%,.2f ₽",BigDecimal.valueOf(value,2)); }
    private LinearLayout column() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private LinearLayout card() { LinearLayout l=column(); l.setPadding(dp(18),dp(16),dp(18),dp(16)); l.setBackground(shape(Color.WHITE)); return l; }
    private LinearLayout dialogBox() { LinearLayout l=column(); l.setPadding(dp(24),dp(12),dp(24),dp(12)); return l; }
    private GradientDrawable shape(int color) { GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(18)); return d; }
    private TextView text(String value,int size,int color) { TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setPadding(0,dp(4),0,dp(4)); return t; }
    private Button button(String value,Runnable action) { Button b=new Button(this); b.setText(value); b.setAllCaps(false); b.setTextColor(GREEN); b.setTextSize(14); b.setBackground(shape(PALE)); b.setOnClickListener(v -> action.run()); return b; }
    private EditText input(String hint,boolean decimal) { EditText e=new EditText(this); e.setHint(hint); e.setTextColor(INK); e.setSingleLine(true); e.setMinHeight(dp(52)); e.setInputType(decimal ? InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL : InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES); return e; }
    private Spinner spinner(List<String> list) { Spinner s=new Spinner(this); ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,list); a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); s.setAdapter(a); s.setMinimumHeight(dp(48)); return s; }
    private void space(LinearLayout parent,int height) { parent.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height))); }
    private void section(String title) { TextView t=text(title,20,INK); t.setTypeface(null,Typeface.BOLD); content.addView(t); space(content,8); }
    private void empty(String title,String body) { LinearLayout c=card(); c.addView(text(title,19,INK)); c.addView(text(body,15,MUTED)); content.addView(c); }
    private void progress(LinearLayout parent,long value,long max,int color) {
        ProgressBar p=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); p.setMax(1000);
        p.setProgress(max<=0?0:(int)Math.min(1000,value*1000.0/max));
        p.setProgressTintList(android.content.res.ColorStateList.valueOf(value>max?RED:color));
        p.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFCDD8CC));
        parent.addView(p,new LinearLayout.LayoutParams(-1,dp(8)));
    }
}
