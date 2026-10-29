#!/usr/bin/env python3
"""Build the Ukrainian PDF guide from the same TSV used by parser tests.
Requires Python 3, reportlab and DejaVu Sans fonts (Debian: fonts-dejavu-core).
Usage: python3 tools/build_language_guide.py /path/to/guide.pdf
"""
import csv
import sys
from pathlib import Path
from xml.sax.saxutils import escape
from reportlab.lib import colors
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, PageBreak, KeepTogether

ROOT = Path(__file__).resolve().parents[1]
FONT = Path('/usr/share/fonts/truetype/dejavu')

def build(destination):
    for name, filename in [('DejaVu', 'DejaVuSans.ttf'), ('DejaVuBold', 'DejaVuSans-Bold.ttf')]:
        pdfmetrics.registerFont(TTFont(name, str(FONT / filename)))
    pdfmetrics.registerFontFamily('DejaVu', normal='DejaVu', bold='DejaVuBold', italic='DejaVu', boldItalic='DejaVuBold')
    styles = getSampleStyleSheet()
    styles.add(ParagraphStyle('UkrBody', fontName='DejaVu', fontSize=10, leading=15, spaceAfter=8, textColor=colors.HexColor('#233638')))
    styles.add(ParagraphStyle('UkrTitle', parent=styles['UkrBody'], fontName='DejaVuBold', fontSize=27, leading=34, spaceAfter=18))
    styles.add(ParagraphStyle('UkrHeading', parent=styles['UkrBody'], fontName='DejaVuBold', fontSize=16, leading=22, spaceBefore=15, spaceAfter=10, keepWithNext=True, textColor=colors.HexColor('#155e58')))
    styles.add(ParagraphStyle('UkrCell', parent=styles['UkrBody'], fontSize=9, leading=13, spaceAfter=0))
    story=[]
    def p(value, style='UkrBody'): return Paragraph(escape(value), styles[style])
    def text(value, style='UkrBody'): story.append(p(value,style))
    def heading(value): text(value,'UkrHeading')
    text('ГолосКалендар','UkrTitle')
    text('Українська мова • версія 4.3.0')
    text('Практичний довідник: час, дати, тривалість, часові проміжки, повторення та цілоденні події.')
    heading('Без мовних моделей')
    text('Введіть опис події українською або заповніть дату, час і назву вручну. Натисніть «Розібрати текст» та перевірте деталі. У цій версії немає запису звуку, системного голосового розпізнавання, мовних моделей або їх завантаження. Українські фрази обробляються локальними правилами, без інтернету.')
    heading('Нагадування про подію')
    text('У формі відкрийте «Нагадування»: виберіть готовий інтервал або «Інший інтервал…» і введіть 0–1440 хвилин до початку. 0 означає нагадування у час початку. Під вибором показано час спрацювання. Збережіть подію в ГолосКалендарі; для цілоденної події окремо оберіть час нагадування. Старі події не отримують нагадування автоматично.')
    text('У «Налаштувати сповіщення» дозвольте повідомлення Android і, за потреби, точний час. Кнопка «Перевірити сповіщення» показує окреме тестове повідомлення без зміни календаря. Це перевірка показу, а не гарантія майбутньої доставки. Примусова зупинка блокує нагадування до запуску застосунку; на Redmi перевірте також обмеження живлення.')
    heading('Як створити подію')
    for value in [
        '1. Натисніть «Додати подію». Введіть команду текстом і натисніть «Розібрати текст».',
        '2. Перевірте назву, дату, час, тривалість, тип події, повторення й нагадування. Відсутні значення оберіть самостійно.',
        '3. Збережіть у ГолосКалендарі. Відкриття системного календаря — окрема дія; збереження там підтверджується в його редакторі.',
        'Приклад: «Заплануй зустріч у п’ятницю о другій дня на пів години» → найближча п’ятниця, 14:00, 30 хвилин.',
        'Парсер працює локально за правилами та регулярними виразами. Мовні моделі не використовуються.'
    ]: text(value)
    heading('Тривалість — лише коли її вказано')
    text('«На годину», «на пів години», «хвилин на двадцять» заповнюють тривалість. Якщо її не названо, поле порожнє. «За пів години» та «через 30 хв» задають початок через 30 хвилин і не додають тривалість. Проміжок з відомими початком і кінцем також задає тривалість.')
    text('«На кілька хвилин» потребує вибору точної тривалості. «Приблизно на годину» дає 60 хвилин із підтвердженням. «До кінця дня» обчислюється лише після відомих дати й часу; без них потрібне уточнення.')
    heading('Перевірка на телефоні')
    text('Перевірте український текст, вибір ранку/вечора, поворот екрана, серію наступного тижня та сповіщення. Зберігайте тривалість порожньою, якщо її не назвали. Після оновлення з тим самим підписом старі локальні події залишаються. Видалення застосунку стирає їх; хмарної копії немає.')
    story.append(PageBreak())
    heading('Як обробляється неоднозначність')
    for value in [
        '«О сьомій», «об одинадцятій», «о пів на третю» без часу доби дають вибір між ранковим та вечірнім варіантами. Застосунок не обирає один із них сам. Числа без часу доби читаються в 24-годинному форматі: «о 9» — 09:00.',
        '«У п’ятницю» — найближча п’ятниця, включно із сьогодні. «Наступного понеділка» та «наступний понеділок» — на тиждень після найближчого понеділка. «Цієї п’ятниці» також означає найближчу п’ятницю. Усі результати показуються у формі перед збереженням.',
        '«Після обіду», «під вечір», «наприкінці місяця», «наступного року» не мають одного точного значення: відповідне поле потрібно вибрати. «Між другою та четвертою», «не раніше десятої», «до сьомої вечора» є часовими обмеженнями, а не точним початком.',
        '«Через день», «двічі на тиждень», «кожного другого вівторка» потребують уточнення повторення. Для двох разів на тиждень виберіть два дні. Для «кожного другого вівторка» оберіть або кожні два тижні, або другий вівторок кожного місяця.',
        '«В останню неділю місяця» без слова «щомісяця» трактується як одна найближча така подія та потребує підтвердження. Для серії виберіть повторення або скажіть «щомісяця в останню неділю місяця».',
        'Суперечливі дати, час або тривалості не замінюються мовчки. Невідомі формулювання можуть залишитися в назві. Перевіряйте розпізнані деталі.'
    ]: text(value)
    heading('Серії та події на весь день')
    for value in [
        'Повторення зберігається однією серією: щодня, кожні два дні, вибрані дні тижня, кожні два тижні, число місяця, порядковий день тижня або дата щороку. Зміна чи видалення стосується всієї серії; винятки для окремого повтору не підтримуються.',
        'Місяці без потрібного числа пропускаються: 31 число не переноситься на 30. Щорічне 29 лютого з’являється лише у високосні роки. Час серії зберігається у первісному часовому поясі; весняне входження у неіснуючий місцевий час пропускається.',
        '«На весь день» не створює вигадані 1440 хвилин. «З п’ятого по десяте листопада» включає 5–10 листопада. Цілоденні дати не зміщуються через подорож до іншого часового поясу.',
        'Для сповіщення про цілоденну подію оберіть окремий точний час нагадування. «Нагадай» вмикає нагадування у час початку, але показ і точність залежать від дозволів Android. Звичайна команда «Заплануй» не вмикає нагадування автоматично.',
        'Зовнішньому календарю передаються цілоденні дати та правило RRULE. Обробка цих полів залежить від редактора календаря. Автоматичної синхронізації з локальним календарем немає.'
    ]: text(value)
    story.append(PageBreak())
    heading('87 прикладів і очікувані результати')
    text('Опорний момент для таблиць: понеділок, 26 жовтня 2026 року, 10:00. Відносні дати на вашому телефоні обчислюються від поточного моменту. Тире означає, що точне поле не визначене; його треба уточнити, якщо воно необхідне для події.')
    with (ROOT/'app/src/test/resources/ukrainian-phrases.tsv').open() as stream:
        rows=list(csv.DictReader(stream,delimiter='\t'))
    assert len(rows)==87
    category=None
    for row in rows:
        if row['category'] != category:
            category=row['category']
            heading(category)
        parts=[]
        if row['date']!='-': parts.append('Дата: '+row['date'])
        if row['time']!='-': parts.append('Час: '+row['time'])
        if row['duration']!='-': parts.append('Тривалість: '+row['duration']+' хв')
        if row['allDay']=='true':
            parts.append('На весь день')
            if row['endExclusive']!='-':
                from datetime import date,timedelta
                parts.append('Останній день: '+str(date.fromisoformat(row['endExclusive'])-timedelta(days=1)))
        if row['recurrence']!='-': parts.append('Повторення: '+row['recurrence'])
        note=row['note']
        if note and note not in (row['date'],row['time'],row['duration']+' хв') and not (row['recurrence']!='-' and note.startswith(row['recurrence'])):
            parts.append(note)
        if row['recurrence']!='-' and row['time']=='-': parts.append('Оберіть точний час')
        if 'C' in row['flags']: parts.append('Потрібне підтвердження')
        result='; '.join(parts)
        table=Table([[p('«'+row['phrase']+'»','UkrCell'),p(result,'UkrCell')]],colWidths=[214,285],hAlign='LEFT')
        table.setStyle(TableStyle([('VALIGN',(0,0),(-1,-1),'TOP'),('BACKGROUND',(0,0),(0,0),colors.HexColor('#e5f2ef')),('LINEBELOW',(0,0),(-1,-1),0.4,colors.HexColor('#c9d9d6')),('LEFTPADDING',(0,0),(-1,-1),8),('RIGHTPADDING',(0,0),(-1,-1),8),('TOPPADDING',(0,0),(-1,-1),7),('BOTTOMPADDING',(0,0),(-1,-1),7)]))
        story.append(KeepTogether([table,Spacer(1,4)]))
    heading('Позначення повторень у таблицях')
    text('D:1 — щодня; D:2 — кожні два дні; W:1 — понеділок щотижня; W:1,2,3,4,5 — будні; W:6,7 — вихідні. W2:2 — вівторок кожні два тижні; M:1 — першого числа місяця; MN:-1:7 — остання неділя місяця; Y:5:15 — щороку 15 травня. У застосунку замість цих кодів показуються українські назви.')
    def footer(canvas,doc):
        canvas.saveState();canvas.setFont('DejaVu',8);canvas.setFillColor(colors.HexColor('#526b69'))
        canvas.drawString(48,27,'ГолосКалендар 4.3.0 • Українські фрази')
        canvas.drawRightString(A4[0]-48,27,str(doc.page));canvas.restoreState()
    destination=Path(destination);destination.parent.mkdir(parents=True,exist_ok=True)
    SimpleDocTemplate(str(destination),pagesize=A4,rightMargin=48,leftMargin=48,topMargin=45,bottomMargin=48,
        title='ГолосКалендар 4.3.0 — українські фрази',author='HolosCalendar').build(story,onFirstPage=footer,onLaterPages=footer)
    return destination

if __name__=='__main__':
    if len(sys.argv)!=2: raise SystemExit('Usage: python3 tools/build_language_guide.py output.pdf')
    print(build(sys.argv[1]))
