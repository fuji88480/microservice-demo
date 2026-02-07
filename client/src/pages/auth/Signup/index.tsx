import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useRegister } from '@/hooks/useRegister';
import { Label } from '@radix-ui/react-label';
import { useState, type FormEvent } from 'react';

export default function Signup() {
  const { mutateAsync, isPending } = useRegister();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    const res = await mutateAsync({ email, password });

    console.log('signup' + res);
  };

  return (
    <div className="flex justify-center font-mono">
      <div className="space-y-6 p-10 mt-50 rounded-2xl border">
        <div className="space-y-2">
          <h1 className="text-2xl">新規登録</h1>
          <p className="text-sm text-muted-foreground">
            新しいアカウントを作成
          </p>
        </div>

        <form className="space-y-4" onSubmit={handleSubmit}>
          <div>
            <Label htmlFor="email">E-Mail</Label>
            <Input
              id="email"
              type="email"
              placeholder="you@example.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </div>

          <div className="mb-10">
            <Label htmlFor="password">Password</Label>
            <Input
              id="password"
              type="password"
              placeholder="********"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>

          <Button
            disabled={isPending}
            variant="destructive"
            className="w-full bg-pink-700"
          >
            新規登録
          </Button>
        </form>
      </div>
    </div>
  );
}
