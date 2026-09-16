#!/bin/bash
# ==============================================================================
# Stickybeak AWS ECR Multi-Service Build & Push Script
# Usage: ./scripts/aws-ecr-push.sh <AWS_ACCOUNT_ID> [AWS_REGION] [TAG]
# Example: ./scripts/aws-ecr-push.sh 123456789012 ap-southeast-2 v1.0.0
# ==============================================================================

set -e

ACCOUNT_ID=${1:-$AWS_ACCOUNT_ID}
REGION=${2:-${AWS_DEFAULT_REGION:-ap-southeast-2}}
TAG=${3:-v1.0.0}

if [ -z "$ACCOUNT_ID" ]; then
  echo "❌ Error: AWS Account ID is required!"
  echo "Usage: $0 <AWS_ACCOUNT_ID> [AWS_REGION] [TAG]"
  exit 1
fi

REGISTRY="${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com"

echo "=================================================================="
echo "🚀 Authenticating with AWS ECR: ${REGISTRY} (${REGION})"
echo "=================================================================="
aws ecr get-login-password --region "${REGION}" | docker login --username AWS --password-stdin "${REGISTRY}"

# Microservices list
SERVICES=(
  "stickybeak-gateway:gateway"
  "stickybeak-auth:auth"
  "stickybeak-product:product"
  "stickybeak-cart:cart"
  "stickybeak-order:order"
  "stickybeak-payment:payment"
  "stickybeak-notification:notification"
)

echo ""
echo "=================================================================="
echo "📦 Building & Pushing Backend Microservices"
echo "=================================================================="

for item in "${SERVICES[@]}"; do
  MODULE="${item%%:*}"
  SVC_NAME="${item##*:}"
  REPO_URI="${REGISTRY}/stickybeak/${SVC_NAME}"

  echo "==> Building ${MODULE}..."
  # Ensure ECR repository exists
  aws ecr describe-repositories --repository-names "stickybeak/${SVC_NAME}" --region "${REGION}" >/dev/null 2>&1 || \
    aws ecr create-repository --repository-name "stickybeak/${SVC_NAME}" --region "${REGION}" >/dev/null

  docker build -f docker/Dockerfile.backend \
    --build-arg MODULE="${MODULE}" \
    -t "${REPO_URI}:${TAG}" \
    -t "${REPO_URI}:latest" .

  echo "==> Pushing ${REPO_URI}:${TAG}..."
  docker push "${REPO_URI}:${TAG}"
  docker push "${REPO_URI}:latest"
  echo "✔ Successfully pushed ${SVC_NAME} to ECR"
done

echo ""
echo "=================================================================="
echo "🌐 Building & Pushing Frontend Reverse Proxy"
echo "=================================================================="

FRONTEND_REPO="${REGISTRY}/stickybeak/frontend"
aws ecr describe-repositories --repository-names "stickybeak/frontend" --region "${REGION}" >/dev/null 2>&1 || \
  aws ecr create-repository --repository-name "stickybeak/frontend" --region "${REGION}" >/dev/null

docker build -f docker/Dockerfile.frontend \
  -t "${FRONTEND_REPO}:${TAG}" \
  -t "${FRONTEND_REPO}:latest" .

echo "==> Pushing ${FRONTEND_REPO}:${TAG}..."
docker push "${FRONTEND_REPO}:${TAG}"
docker push "${FRONTEND_REPO}:latest"

echo ""
echo "=================================================================="
echo "🎉 All Stickybeak images successfully pushed to Amazon ECR!"
echo "Registry: ${REGISTRY}/stickybeak/*"
echo "Release Tag: ${TAG}"
echo "=================================================================="
