#!/usr/bin/env bash
# Прогоняет тот же mvn-гейт, что и CI (.github/workflows/ci.yml: "mvn clean verify"),
# и печатает реальный процент покрытия по бандлу (а не только pass/fail),
# чтобы можно было увидеть цифру ДО пуша в GitHub, а не гадать по логу jacoco:check.
#
# Использование:
#   ./scripts/check-coverage.sh
#
# Код возврата совпадает с кодом возврата mvn — скрипт можно использовать
# как pre-push хук или просто как быстрый локальный аналог CI-джобы.

set -uo pipefail

THRESHOLD="0.89"

echo "==> mvn clean verify"
mvn clean verify
MVN_EXIT=$?

XML="target/site/jacoco/jacoco.xml"

echo ""
if [ ! -f "$XML" ]; then
    echo "jacoco.xml не найден по пути $XML — отчёт не сгенерировался (mvn упал раньше, чем report/check)."
    exit "$MVN_EXIT"
fi

# jacoco.xml jacoco-maven-plugin пишет в одну строку без переносов, поэтому
# просто берём ПОСЛЕДНЕЕ вхождение каждого счётчика — оно всегда идёт прямо
# перед закрывающим </report> и относится к бандлу целиком (после всех
# package-уровневых счётчиков). Именно эти числа jacoco:check сравнивает
# с порогом (см. pom.xml, rule BUNDLE).
print_ratio() {
    local type="$1"
    local line missed covered
    line=$(grep -oE "<counter type=\"$type\" missed=\"[0-9]+\" covered=\"[0-9]+\"/>" "$XML" | tail -1)
    if [ -z "$line" ]; then
        echo "  $type: не найдено в отчёте"
        return
    fi
    missed=$(echo "$line" | sed -E 's/.*missed="([0-9]+)".*/\1/')
    covered=$(echo "$line" | sed -E 's/.*covered="([0-9]+)".*/\1/')
    awk -v c="$covered" -v m="$missed" -v t="$type" -v thr="$THRESHOLD" 'BEGIN {
        total = c + m;
        ratio = (total > 0) ? c / total : 0;
        status = (ratio >= thr) ? "OK" : "НИЖЕ ПОРОГА";
        printf "  %-11s %d / %d покрыто = %.2f%%  (порог %.0f%%)  [%s]\n", t":", c, total, ratio*100, thr*100, status;
    }'
}

echo "==> Coverage по бандлу 'food-packaging' (то же, что проверяет jacoco:check)"
print_ratio "INSTRUCTION"
print_ratio "LINE"
echo ""

if [ "$MVN_EXIT" -eq 0 ]; then
    echo "mvn verify: SUCCESS — можно пушить, CI (mvn clean verify) должен пройти так же."
else
    echo "mvn verify: FAILURE — CI тоже упадёт. Смотри цифры выше и/или полный лог mvn."
fi

exit "$MVN_EXIT"
