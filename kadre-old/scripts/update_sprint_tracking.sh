#!/bin/bash

# Script pour mettre à jour le fichier SPRINT_TRACKING.md
# à partir des issues GitHub

REPO="ygdrasil-io/poc-koreos"
TRACKING_FILE="SPRINT_TRACKING.md"

# Fonction pour obtenir les issues d'un sprint
get_sprint_issues() {
    local sprint=$1
    gh issue list --repo $REPO --label "sprint: $sprint" --json number,title,priority,labels,state,assignees \
        --jq ".[] | {
            number: .number,
            title: .title,
            priority: (.labels[] | select(.name | startswith(\"priority:\")) | .name | split(\": \")[1]),
            category: (.labels[] | select(.name | startswith(\"category:\")) | .name | split(\": \")[1]),
            backend: (.labels[] | select(.name | startswith(\"backend:\")) | .name | split(\": \")[1]),
            phase: (.labels[] | select(.name | startswith(\"phase:\")) | .name | split(\": \")[1]),
            status: (.labels[] | select(.name | startswith(\"status:\")) | .name | split(\": \")[1]),
            state: .state,
            assignee: (.assignees[0].login // \"-\")
        }"
}

# Fonction pour compter les tickets par sprint
count_sprint_tickets() {
    local sprint=$1
    echo $(get_sprint_issues $sprint | jq 'length')
}

# Fonction pour obtenir les stats par sprint
get_sprint_stats() {
    local sprint=$1
    local total=$(count_sprint_tickets $sprint)
    local p0=$(get_sprint_issues $sprint | jq '[.[] | select(.priority == "P0")] | length')
    local p1=$(get_sprint_issues $sprint | jq '[.[] | select(.priority == "P1")] | length')
    local p2=$(get_sprint_issues $sprint | jq '[.[] | select(.priority == "P2")] | length')
    local p3=$(get_sprint_issues $sprint | jq '[.[] | select(.priority == "P3")] | length')
    local done=$(get_sprint_issues $sprint | jq '[.[] | select(.status == "done")] | length')
    
    echo "$total,$p0,$p1,$p2,$p3,$done"
}

echo "Mise à jour du tableau de bord des sprints..."
echo ""

# Obtenir les stats pour chaque sprint
for sprint in 1 2 3 4 5 6; do
    stats=$(get_sprint_stats $sprint)
    IFS=',' read -r total p0 p1 p2 p3 done <<< "$stats"
    
    if [ $total -gt 0 ]; then
        pct=$((done * 100 / total))
        echo "Sprint $sprint: $done/$total tickets ($pct%) - P0:$p0, P1:$p1, P2:$p2, P3:$p3"
    fi
done

echo ""
echo "Génération du rapport détaillé..."

# Générer un rapport Markdown
cat > /tmp/sprint_report.md << 'EOF'
# 📊 Rapport de Progrès des Sprints - $(date +%Y-%m-%d)

## 📈 Statistiques Globales

EOF

# Ajouter les stats par sprint
for sprint in 1 2 3; do
    stats=$(get_sprint_stats $sprint)
    IFS=',' read -r total p0 p1 p2 p3 done <<< "$stats"
    
    if [ $total -gt 0 ]; then
        pct=$((done * 100 / total))
        echo "### Sprint $sprint" >> /tmp/sprint_report.md
        echo "- Total: $total tickets" >> /tmp/sprint_report.md
        echo "- Terminés: $done ($pct%)" >> /tmp/sprint_report.md
        echo "- P0: $p0, P1: $p1, P2: $p2, P3: $p3" >> /tmp/sprint_report.md
        echo "" >> /tmp/sprint_report.md
    fi
done

# Ajouter la liste des tickets par sprint
for sprint in 1 2 3; do
    echo "## Sprint $sprint - Détails" >> /tmp/sprint_report.md
    echo "" >> /tmp/sprint_report.md
    
    get_sprint_issues $sprint | jq -r '.[] | 
        "| \(.number) | [\(.title)](https://github.com/ygdrasil-io/poc-koreos/issues/\(.number)) | \(.priority) | \(.category // "-") | \(.backend // "-") | \(.status // "-") | \(.assignee) |"' \
        >> /tmp/sprint_report.md
    
    echo "" >> /tmp/sprint_report.md
done

cat /tmp/sprint_report.md

echo ""
echo "✅ Rapport généré dans /tmp/sprint_report.md"
echo ""
echo "Pour mettre à jour SPRINT_TRACKING.md manuellement:"
echo "1. Copiez les informations de ce rapport"
echo "2. Collez-les dans SPRINT_TRACKING.md"
echo "3. Mettez à jour les burndown charts manuellement"
echo ""
echo "Pour automatiser complètement, utilisez:"
echo "gh project create (quand disponible)"
echo "ou un outil comme Jira avec intégration GitHub"
